package dev.opecko.sonocraft.api.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.api.ApiMethods;
import dev.opecko.sonocraft.api.ConnectionClosedException;
import dev.opecko.sonocraft.api.ErrorCode;
import dev.opecko.sonocraft.api.Json;
import dev.opecko.sonocraft.api.LocalRateLimitException;
import dev.opecko.sonocraft.api.NotAvailableException;
import dev.opecko.sonocraft.api.NotConnectedException;
import dev.opecko.sonocraft.api.RateClass;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.api.Topic;
import dev.opecko.sonocraft.api.model.AccountState;
import dev.opecko.sonocraft.api.model.HelloResult;
import dev.opecko.sonocraft.api.model.RegistrationOutcome;
import dev.opecko.sonocraft.api.model.RegistrationPending;
import dev.opecko.sonocraft.api.model.RequestScopesResult;
import dev.opecko.sonocraft.api.model.RevokeInfo;
import dev.opecko.sonocraft.api.model.ScopesChanged;
import dev.opecko.sonocraft.api.rpc.RpcConnection;
import dev.opecko.sonocraft.platform.Connector;
import dev.opecko.sonocraft.platform.Transport;
import dev.opecko.sonocraft.platform.token.TokenStore;
import dev.opecko.sonocraft.platform.token.TokenStoreException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Sonolith connection lifecycle: registration, hello, event subscription, reconnect and end of access
 * (SPEC §3.4, §6, §7, §9, §10; PLAN §3.3). Modeled on {@code api/examples/python-tui/sonolith_tui/session.py}.
 *
 * <p>Rules:
 * <ul>
 *   <li>app.register only after {@link #connect()} (user action), never automatically.
 *   <li>The connection stays open while waiting for approval. After approval the token is stored first thing.
 *   <li>Unauthorized / NotRegistered / session.revoked: delete the token and wait for the user.
 *   <li>Sonolith unavailable: backoff 1 s → 60 s, reset after a successful hello.
 *   <li>Only granted scopes are used; RateLimited holds the class for retryAfterMs.
 * </ul>
 *
 * <p>Threads: the session loop runs on "Sonocraft-IO" (may block), events and listeners on "Sonocraft-Events",
 * disconnect on "Sonocraft-Action". Public methods are thread-safe and non-blocking.
 */
public final class SonolithSession implements AutoCloseable {
    private static final long HELLO_TIMEOUT_MS = 10_000;
    private static final long UNREGISTER_TIMEOUT_MS = 5_000;
    private static final long REGISTER_TIMEOUT_MS = 35_000;

    /** Result of {@link #connect()}. */
    public enum ConnectResult {
        STARTED,
        /** Already connecting or connected. */
        BUSY,
        /** The user recently denied; retry only after {@link SessionStatus#retryAtMs()}. */
        COOLDOWN
    }

    /** Why the current connection is being closed on purpose. */
    private enum Ending { CANCELLED, UNREGISTERED, REVOKED }

    private final AppRegistration registration;
    private final TokenStore tokens;
    private final Connector connector;
    private final Backoff backoff;
    private final RateLimiter rateLimiter;
    private final LongSupplier clock;
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService events = daemonExecutor("Sonocraft-Events");
    private final ExecutorService actions = daemonExecutor("Sonocraft-Action");
    private final Outcomes outcomes = new Outcomes();

    private final Object statusLock = new Object();
    private volatile SessionStatus status = SessionStatus.INITIAL;
    private volatile Supplier<SubscriptionOptions> subscriptionOptions = () -> SubscriptionOptions.DEFAULT;

    private final ReentrantLock wakeLock = new ReentrantLock();
    private final Condition wakeCondition = wakeLock.newCondition();
    private boolean woken;

    private final AtomicBoolean connectRequested = new AtomicBoolean();
    private volatile boolean hold;
    private volatile boolean stopping;
    private volatile Ending ending;
    private volatile RpcConnection conn;
    private volatile long scopeRetryAtMs;
    private volatile String pendingScopeRequest;
    private Thread loop;

    public SonolithSession(AppRegistration registration, TokenStore tokens, Connector connector) {
        this(registration, tokens, connector, new Backoff(), new RateLimiter(), System::currentTimeMillis);
    }

    public SonolithSession(AppRegistration registration, TokenStore tokens, Connector connector, Backoff backoff,
                           RateLimiter rateLimiter, LongSupplier clock) {
        this.registration = registration;
        this.tokens = tokens;
        this.connector = connector;
        this.backoff = backoff;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    private static ExecutorService daemonExecutor(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------------------------------------------------------ public API

    public void addListener(SessionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /** Subscription options, read on every subscribe (after connecting and on scope changes). */
    public void setSubscriptionOptions(Supplier<SubscriptionOptions> options) {
        this.subscriptionOptions = options;
    }

    public SessionStatus status() {
        return status;
    }

    public AppRegistration registration() {
        return registration;
    }

    /** Starts the loop. With a stored token it connects right away; without one it waits for {@link #connect()}. */
    public synchronized void start() {
        if (loop == null) {
            loop = new Thread(this::runLoop, "Sonocraft-IO");
            loop.setDaemon(true);
            loop.start();
        }
    }

    /** Stops the loop and closes the connection (on game shutdown). */
    @Override
    public void close() {
        stopping = true;
        RpcConnection c = conn;
        if (c != null) {
            c.close();
        }
        wake();
        Thread t;
        synchronized (this) {
            t = loop;
        }
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        events.shutdown();
        actions.shutdown();
    }

    /**
     * The user clicked "Connect to Sonolith" (or "Try again"). Without a token it starts registration; with a token
     * it tries to connect right away (cancelling backoff and waiting for the user).
     */
    public ConnectResult connect() {
        SessionStatus s = status;
        switch (s.state()) {
            case CONNECTED, CONNECTING, REGISTERING, IDENTITY_PENDING -> {
                return ConnectResult.BUSY;
            }
            case COOLDOWN -> {
                long remaining = s.remainingMs(clock.getAsLong());
                if (remaining > 0) {
                    notice(new Notice(Notice.Kind.REGISTER_COOLDOWN, remaining));
                    return ConnectResult.COOLDOWN;
                }
            }
            default -> {
            }
        }
        connectRequested.set(true);
        hold = false;
        backoff.reset();
        wake();
        return ConnectResult.STARTED;
    }

    /** Withdraws the pending request: closing the connection cancels it in Sonolith (SPEC §6.1). */
    public void cancelRegistration() {
        SessionState st = status.state();
        RpcConnection c = conn;
        if ((st == SessionState.REGISTERING || st == SessionState.IDENTITY_PENDING) && c != null) {
            ending = Ending.CANCELLED;
            c.close();
        }
    }

    /**
     * "Disconnect from Sonolith" (SPEC §9.1): app.unregister and token deletion, even when Sonolith isn't running.
     * The future returns true if Sonolith was reached.
     */
    public CompletableFuture<Boolean> disconnect() {
        return CompletableFuture.supplyAsync(this::doDisconnect, actions);
    }

    /**
     * Requests more scopes (SPEC §6.5), only on user action. The outcome arrives as an event; the future returns
     * the immediate answer (pending/granted), or null during cooldown.
     */
    public CompletableFuture<RequestScopesResult> requestScopes(Collection<Scope> wanted) {
        long left = scopeRetryAtMs - clock.getAsLong();
        if (scopeRetryAtMs != 0 && left > 0) {
            notice(new Notice(Notice.Kind.SCOPES_COOLDOWN, left));
            return CompletableFuture.completedFuture(null);
        }
        scopeRetryAtMs = 0;
        JsonObject params = new JsonObject();
        params.add("scopes", registration.scopeRequests(wanted));
        return call("app.requestScopes", params).handleAsync((result, error) -> {
            if (error != null) {
                Throwable cause = unwrap(error);
                if (cause instanceof SonolithException e && e.is(ErrorCode.REGISTRATION_DENIED)) {
                    scopeRetryAtMs = clock.getAsLong() + Math.max(0, e.retryAfterMs());
                }
                throw new CompletionException(cause);
            }
            RequestScopesResult r = Json.parse(result, RequestScopesResult.class);
            if (r != null && "pending".equals(r.status())) {
                pendingScopeRequest = r.requestId();
                notice(new Notice(Notice.Kind.SCOPES_REQUESTED, 0));
            } else if (r != null && "granted".equals(r.status())) {
                notice(new Notice(Notice.Kind.SCOPES_ALREADY_GRANTED, 0));
            }
            return r;
        }, events);
    }

    /**
     * Calls an API method. Doesn't send what Sonolith would reject anyway: no connection ({@link NotConnectedException}),
     * no scope (PermissionDenied) or over the rate limit ({@link LocalRateLimitException}). Dependent stages run on
     * the Sonocraft-Events thread.
     */
    public CompletableFuture<JsonElement> call(String method, JsonObject params) {
        RpcConnection c = conn;
        SessionStatus s = status;
        if (c == null || s.state() != SessionState.CONNECTED) {
            return CompletableFuture.failedFuture(new NotConnectedException(method));
        }
        List<Scope> needed = ApiMethods.scopes(method);
        if (!needed.isEmpty() && needed.stream().noneMatch(s::has)) {
            return CompletableFuture.failedFuture(SonolithException.permissionDenied(method, needed.get(0)));
        }
        RateClass rate = ApiMethods.rateClass(method);
        long wait = rateLimiter.tryAcquire(rate);
        if (wait > 0) {
            return CompletableFuture.failedFuture(new LocalRateLimitException(method, rate, wait));
        }
        return c.call(method, params).whenCompleteAsync((r, error) -> {
            if (unwrap(error) instanceof SonolithException e && e.is(ErrorCode.RATE_LIMITED)) {
                rateLimiter.hold(rate, e.retryAfterMs() > 0 ? e.retryAfterMs() : 1000);
            }
        }, events);
    }

    // ------------------------------------------------------------------ loop

    private void runLoop() {
        while (!stopping) {
            ending = null;
            boolean userAsked = connectRequested.getAndSet(false);
            String token;
            try {
                token = tokens.load();
            } catch (TokenStoreException e) {
                Sonocraft.LOGGER.warn("Cannot read the Sonolith token: {}", e.getMessage());
                long delay = backoff.next(0);
                update(s -> s.with(SessionState.UNAVAILABLE, StatusReason.TOKEN_STORE_FAILED)
                        .withRetryAt(clock.getAsLong() + delay).withDetail(e.getMessage()));
                sleepOrWake(delay);
                continue;
            }
            boolean registering = token == null && userAsked;
            if ((token == null && !registering) || (token != null && hold)) {
                if (token == null) {
                    update(s -> Set.of(SessionState.NOT_CONNECTED, SessionState.COOLDOWN, SessionState.BLOCKED)
                            .contains(s.state()) ? s : s.with(SessionState.NOT_CONNECTED, StatusReason.NONE).withGranted(Set.of()));
                }
                sleepOrWake(Long.MAX_VALUE);
                continue;
            }
            update(s -> s.with(SessionState.CONNECTING, StatusReason.NONE));
            Transport transport;
            try {
                transport = connector.open();
            } catch (NotAvailableException e) {
                Sonocraft.LOGGER.debug("Sonolith not available: {}", e.getMessage());
                if (registering) {
                    update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.NOT_AVAILABLE));
                    continue;
                }
                long delay = backoff.next(0);
                update(s -> s.with(SessionState.UNAVAILABLE, StatusReason.NOT_AVAILABLE)
                        .withRetryAt(clock.getAsLong() + delay).withGranted(Set.of()));
                sleepOrWake(delay);
                continue;
            }
            ConnectionListener listener = new ConnectionListener();
            RpcConnection c = new RpcConnection(transport, listener);
            listener.connection = c;
            conn = c;
            c.start();
            long retryAfter = 0;
            boolean wasConnected = false;
            try {
                if (registering) {
                    token = register(c);
                }
                if (token != null) {
                    retryAfter = helloAndServe(c, token);
                    wasConnected = retryAfter == 0;
                } else {
                    retryAfter = -1;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                Sonocraft.LOGGER.error("Sonolith session failed", e);
                retryAfter = 1000;
            } finally {
                c.close();
                conn = null;
            }
            if (stopping) {
                break;
            }
            Ending end = ending;
            if (retryAfter < 0 || end != null) {
                continue; // access ended or the user is needed: the next round waits for them
            }
            long delay = backoff.next(retryAfter);
            StatusReason reason = wasConnected ? StatusReason.CONNECTION_LOST : StatusReason.NOT_AVAILABLE;
            update(s -> {
                if (s.state() == SessionState.UNAVAILABLE || s.reason() == StatusReason.ERROR) {
                    return s.withRetryAt(clock.getAsLong() + delay);
                }
                return s.with(SessionState.UNAVAILABLE, reason).withRetryAt(clock.getAsLong() + delay)
                        .withGranted(Set.of());
            });
            sleepOrWake(delay);
        }
    }

    /** Forwards notifications to the event thread; knows its connection so old-connection events don't mix with the new one. */
    private final class ConnectionListener implements RpcConnection.Listener {
        volatile RpcConnection connection;

        @Override
        public void onNotification(String method, JsonObject params) {
            RpcConnection from = connection;
            events.execute(() -> onConnectionEvent(from, method, params));
        }
    }

    /** app.register and waiting for the user's answer. Returns the token, or null. */
    private String register(RpcConnection c) throws InterruptedException {
        RegistrationPending pending;
        try {
            JsonElement r = c.call("app.register", registration.registerParams())
                    .get(REGISTER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            pending = Json.parse(r, RegistrationPending.class);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SonolithException se) {
                registrationRefused(se);
            } else {
                update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.NOT_AVAILABLE));
            }
            return null;
        } catch (TimeoutException e) {
            update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.ERROR).withDetail("app.register timeout"));
            return null;
        }
        if (pending == null || pending.requestId() == null) {
            update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.ERROR).withDetail("unexpected answer"));
            return null;
        }
        update(s -> s.with(SessionState.REGISTERING, StatusReason.WAITING_FOR_APPROVAL)
                .withIdentity(pending.identity()));
        RegistrationOutcome outcome = outcomes.await(pending.requestId(), c);
        if (outcome == null) {
            StatusReason why = ending == Ending.CANCELLED ? StatusReason.REQUEST_CANCELLED : StatusReason.REQUEST_CLOSED;
            update(s -> s.with(SessionState.NOT_CONNECTED, why));
            return null;
        }
        if ("approved".equals(outcome.status()) && outcome.token() != null) {
            // the token arrives only once: store it before anything else
            try {
                tokens.save(outcome.token());
            } catch (TokenStoreException e) {
                Sonocraft.LOGGER.error("Cannot store the Sonolith token; it will be lost after a restart: {}",
                        e.getMessage());
            }
            Set<Scope> granted = Scope.parseAll(outcome.grantedScopes());
            update(s -> s.withGranted(granted));
            return outcome.token();
        }
        resolvedNotApproved(outcome, false);
        return null;
    }

    private void registrationRefused(SonolithException e) {
        switch (e.error()) {
            case BLOCKED -> update(s -> s.with(SessionState.BLOCKED, StatusReason.BLOCKED));
            case REGISTRATION_DENIED -> update(s -> s.with(SessionState.COOLDOWN, StatusReason.DENIED)
                    .withRetryAt(clock.getAsLong() + Math.max(0, e.retryAfterMs())));
            case REGISTRATION_PENDING -> update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.ALREADY_PENDING));
            default -> update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.ERROR)
                    .withDetail(e.error().wireName() + (e.reason() != null ? " (" + e.reason() + ")" : "")));
        }
    }

    private void resolvedNotApproved(RegistrationOutcome outcome, boolean identity) {
        long retryAt = outcome.retryAfterMs() != null ? clock.getAsLong() + outcome.retryAfterMs() : 0;
        if ("blocked".equals(outcome.status())) {
            deleteToken();
            update(s -> s.with(SessionState.BLOCKED, StatusReason.BLOCKED));
        } else if (identity) {
            update(s -> s.with(SessionState.STOPPED, StatusReason.IDENTITY_NOT_APPROVED).withRetryAt(retryAt));
        } else {
            StatusReason why = "expired".equals(outcome.status()) ? StatusReason.EXPIRED : StatusReason.DENIED;
            update(s -> s.with(SessionState.COOLDOWN, why).withRetryAt(retryAt));
        }
    }

    /**
     * Hello, subscribe and serve until the connection ends. Returns the minimum delay before the next attempt in ms,
     * or -1 if the next connection needs the user.
     */
    private long helloAndServe(RpcConnection c, String token) throws InterruptedException {
        HelloResult hello;
        while (true) {
            try {
                JsonElement r = c.call("session.hello", helloParams(token)).get(HELLO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                hello = Json.parse(r, HelloResult.class);
                break;
            } catch (TimeoutException e) {
                return 0;
            } catch (ExecutionException ex) {
                if (!(ex.getCause() instanceof SonolithException e)) {
                    return 0; // connection dropped
                }
                switch (e.error()) {
                    case UNAUTHORIZED, NOT_REGISTERED -> {
                        deleteToken();
                        update(s -> s.with(SessionState.NOT_CONNECTED, StatusReason.ACCESS_ENDED).withGranted(Set.of()));
                        return -1;
                    }
                    case BLOCKED -> {
                        deleteToken();
                        update(s -> s.with(SessionState.BLOCKED, StatusReason.BLOCKED).withGranted(Set.of()));
                        return -1;
                    }
                    case UNSUPPORTED_VERSION -> {
                        hold = true;
                        update(s -> s.with(SessionState.STOPPED, StatusReason.UNSUPPORTED_VERSION));
                        return -1;
                    }
                    case REGISTRATION_PENDING -> {
                        String rid = e.requestId();
                        if (rid == null) {
                            return 1000;
                        }
                        update(s -> s.with(SessionState.IDENTITY_PENDING, StatusReason.IDENTITY_CHANGED));
                        RegistrationOutcome outcome = outcomes.await(rid, c);
                        if (outcome == null) {
                            return 0;
                        }
                        if ("approved".equals(outcome.status())) {
                            continue; // the same token is now valid from here too: hello again
                        }
                        hold = !"blocked".equals(outcome.status());
                        resolvedNotApproved(outcome, true);
                        return -1;
                    }
                    default -> {
                        update(s -> s.withDetail(e.error().wireName()));
                        return Math.max(0, e.retryAfterMs());
                    }
                }
            }
        }
        if (hello == null) {
            return 1000;
        }
        HelloResult h = hello;
        backoff.reset();
        Set<Scope> granted = Scope.parseAll(hello.grantedScopes());
        update(s -> s.with(SessionState.CONNECTED, StatusReason.NONE).withGranted(granted)
                .withSignedIn(h.signedIn()).withIdentity(h.identity())
                .withVersions(h.apiVersion(), h.appVersion()));
        subscribe(c, topicsFor(granted));
        try {
            c.closed().get();
        } catch (ExecutionException e) {
            // closed() never completes exceptionally
        }
        // session.revoked arrives right before the close: handle it before the loop decides what's next
        flushEvents();
        return 0;
    }

    private JsonObject helloParams(String token) {
        JsonObject o = new JsonObject();
        o.addProperty("appId", registration.appId());
        o.addProperty("token", token);
        o.addProperty("apiVersion", ApiMethods.API_VERSION);
        return o;
    }

    /** Topics based on granted scopes and options (SPEC §10.1). */
    List<Topic> topicsFor(Set<Scope> granted) {
        SubscriptionOptions opts = subscriptionOptions.get();
        List<Topic> out = new ArrayList<>();
        for (Topic t : Topic.values()) {
            if (t == Topic.LYRICS_WORD && !opts.lyricsWords()) {
                continue;
            }
            if (t.scope() == null || granted.contains(t.scope())) {
                out.add(t);
            }
        }
        return out;
    }

    private void subscribe(RpcConnection c, List<Topic> topics) {
        if (topics.isEmpty()) {
            return;
        }
        JsonArray arr = new JsonArray();
        topics.forEach(t -> arr.add(t.wire()));
        JsonObject params = new JsonObject();
        params.add("topics", arr);
        params.addProperty("positionHz", subscriptionOptions.get().positionHz());
        c.call("events.subscribe", params).whenCompleteAsync((r, error) -> {
            if (error != null && !(unwrap(error) instanceof ConnectionClosedException)) {
                Sonocraft.LOGGER.warn("events.subscribe failed: {}", unwrap(error).getMessage());
                notice(new Notice(Notice.Kind.SUBSCRIBE_FAILED, 0));
            }
        }, events);
    }

    /** Changes position rate or word lyrics at runtime (re-subscribes with the new options). */
    public void resubscribe() {
        RpcConnection c = conn;
        if (c != null && status.state() == SessionState.CONNECTED) {
            List<Topic> topics = topicsFor(status.granted());
            if (!subscriptionOptions.get().lyricsWords() && status.has(Scope.LYRICS_READ)) {
                JsonArray arr = new JsonArray();
                arr.add(Topic.LYRICS_WORD.wire());
                JsonObject params = new JsonObject();
                params.add("topics", arr);
                c.call("events.unsubscribe", params);
            }
            subscribe(c, topics);
        }
    }

    // ------------------------------------------------------------------ disconnect

    private boolean doDisconnect() {
        connectRequested.set(false);
        hold = false;
        boolean reached = false;
        try {
            // drop the token before Sonolith closes the connection so the loop doesn't retry with the old token
            String token = tokens.load();
            deleteToken();
            RpcConnection c = conn;
            if (status.state() == SessionState.CONNECTED && c != null) {
                ending = Ending.UNREGISTERED;
                c.call("app.unregister", null).get(UNREGISTER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                reached = true;
            } else {
                if (token != null) {
                    try (RpcConnection temp = new RpcConnection(connector.open(), (m, p) -> { }).start()) {
                        temp.call("session.hello", helloParams(token)).get(UNREGISTER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        temp.call("app.unregister", null).get(UNREGISTER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        reached = true;
                    }
                } else {
                    reached = true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Sonocraft.LOGGER.info("app.unregister did not reach Sonolith: {}", unwrap(e).getMessage());
        } finally {
            deleteToken();
            RpcConnection c = conn;
            if (c != null) {
                ending = Ending.UNREGISTERED;
                c.close();
            }
            StatusReason why = reached ? StatusReason.DISCONNECTED : StatusReason.DISCONNECTED_UNREACHABLE;
            update(s -> s.with(SessionState.NOT_CONNECTED, why).withGranted(Set.of()).withIdentity(null));
            wake();
        }
        return reached;
    }

    // ------------------------------------------------------------------ events

    private void onConnectionEvent(RpcConnection from, String method, JsonObject params) {
        boolean current = from == conn;
        switch (method) {
            case "registration.resolved" -> {
                RegistrationOutcome o = Json.parse(params, RegistrationOutcome.class);
                if (o != null) {
                    outcomes.put(o);
                    if ("scopes".equals(o.kind())) {
                        scopeOutcome(o);
                    }
                }
            }
            case "session.revoked" -> {
                if (current) {
                    ending = Ending.REVOKED;
                    deleteToken();
                    RevokeInfo info = Json.parse(params, RevokeInfo.class);
                    String reason = info != null && info.reason() != null ? info.reason() : "removed";
                    StatusReason why = switch (reason) {
                        case "blocked" -> StatusReason.REVOKED_BLOCKED;
                        case "unregistered" -> StatusReason.REVOKED_UNREGISTERED;
                        case "replaced" -> StatusReason.REVOKED_REPLACED;
                        default -> StatusReason.REVOKED_REMOVED;
                    };
                    SessionState st = why == StatusReason.REVOKED_BLOCKED ? SessionState.BLOCKED : SessionState.NOT_CONNECTED;
                    update(s -> s.with(st, why).withGranted(Set.of()));
                }
            }
            case "session.scopesChanged" -> {
                ScopesChanged sc = Json.parse(params, ScopesChanged.class);
                if (current && sc != null && sc.grantedScopes() != null) {
                    Set<Scope> old = status.granted();
                    Set<Scope> now = Scope.parseAll(sc.grantedScopes());
                    update(s -> s.withGranted(now));
                    List<Topic> added = new ArrayList<>(topicsFor(now));
                    added.removeAll(topicsFor(old));
                    if (!added.isEmpty() && from != null) {
                        subscribe(from, added);
                    }
                }
            }
            case "account.changed" -> {
                AccountState a = Json.parse(params, AccountState.class);
                if (current && a != null) {
                    update(s -> s.withSignedIn(a.signedIn()));
                }
            }
            default -> {
            }
        }
        if (current) {
            for (SessionListener l : listeners) {
                try {
                    l.onEvent(method, params);
                } catch (RuntimeException e) {
                    Sonocraft.LOGGER.error("Sonolith event listener failed for {}", method, e);
                }
            }
        }
    }

    private void scopeOutcome(RegistrationOutcome o) {
        if (o.requestId() != null && o.requestId().equals(pendingScopeRequest)) {
            pendingScopeRequest = null;
        }
        if ("approved".equals(o.status())) {
            notice(new Notice(Notice.Kind.SCOPES_GRANTED, 0));
            return;
        }
        long retry = o.retryAfterMs() != null ? o.retryAfterMs() : 0;
        if (retry > 0) {
            scopeRetryAtMs = clock.getAsLong() + retry;
        }
        Notice.Kind kind = switch (o.status() == null ? "" : o.status()) {
            case "blocked" -> Notice.Kind.SCOPES_BLOCKED;
            case "expired" -> Notice.Kind.SCOPES_EXPIRED;
            default -> Notice.Kind.SCOPES_DENIED;
        };
        notice(new Notice(kind, retry));
    }

    // ------------------------------------------------------------------ helpers

    private void deleteToken() {
        try {
            tokens.delete();
        } catch (TokenStoreException e) {
            Sonocraft.LOGGER.error("Cannot delete the Sonolith token: {}", e.getMessage());
        }
    }

    private void update(UnaryOperator<SessionStatus> change) {
        SessionStatus next;
        synchronized (statusLock) {
            SessionStatus prev = status;
            next = change.apply(prev);
            if (next.equals(prev)) {
                return;
            }
            status = next;
        }
        SessionStatus snapshot = next;
        events.execute(() -> {
            for (SessionListener l : listeners) {
                try {
                    l.onStatus(snapshot);
                } catch (RuntimeException e) {
                    Sonocraft.LOGGER.error("Sonolith status listener failed", e);
                }
            }
        });
    }

    private void notice(Notice n) {
        events.execute(() -> {
            for (SessionListener l : listeners) {
                try {
                    l.onNotice(n);
                } catch (RuntimeException e) {
                    Sonocraft.LOGGER.error("Sonolith notice listener failed", e);
                }
            }
        });
    }

    private void wake() {
        wakeLock.lock();
        try {
            woken = true;
            wakeCondition.signalAll();
        } finally {
            wakeLock.unlock();
        }
    }

    private void sleepOrWake(long ms) {
        wakeLock.lock();
        try {
            long deadline = ms == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + ms * 1_000_000;
            while (!woken && !stopping) {
                if (deadline == Long.MAX_VALUE) {
                    wakeCondition.await();
                } else {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        break;
                    }
                    wakeCondition.awaitNanos(left);
                }
            }
            woken = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopping = true;
        } finally {
            wakeLock.unlock();
        }
    }

    /** Waits until the event thread has processed everything already queued. Don't call from the event thread. */
    private void flushEvents() {
        try {
            events.submit(() -> { }).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | java.util.concurrent.RejectedExecutionException e) {
            // event thread is shutting down or stuck in a listener; don't wait any longer
        }
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * registration.resolved by requestId. The event may arrive before anyone starts waiting for it, so unclaimed
     * results are kept.
     */
    private final class Outcomes {
        private final Map<String, RegistrationOutcome> seen = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<RegistrationOutcome>> waiters = new ConcurrentHashMap<>();

        synchronized void put(RegistrationOutcome o) {
            if (o.requestId() == null) {
                return;
            }
            CompletableFuture<RegistrationOutcome> f = waiters.remove(o.requestId());
            if (f != null) {
                f.complete(o);
            } else {
                seen.put(o.requestId(), o);
            }
        }

        /** The result, or null if the connection closed first. */
        RegistrationOutcome await(String requestId, RpcConnection c) throws InterruptedException {
            CompletableFuture<RegistrationOutcome> f;
            synchronized (this) {
                RegistrationOutcome o = seen.remove(requestId);
                if (o != null) {
                    return o;
                }
                f = new CompletableFuture<>();
                waiters.put(requestId, f);
            }
            try {
                CompletableFuture.anyOf(f, c.closed()).get();
                if (!f.isDone()) {
                    // the result may have arrived right before the close and still be waiting in the event queue
                    flushEvents();
                }
                return f.getNow(null);
            } catch (ExecutionException e) {
                return null;
            } finally {
                waiters.remove(requestId);
            }
        }
    }
}
