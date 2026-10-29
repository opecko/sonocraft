plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    // //? if sdl { … } for 26.3+ (SDL instead of GLFW)
    constants["sdl"] = eval(current.version, ">=26.3")
}
