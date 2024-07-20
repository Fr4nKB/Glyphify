package com.frank.glyphify

object Constants {
    const val GLYPH_DEFAULT_INTESITY = 4000
    const val GLYPH_MAX_INTENSITY = 4095
    const val LIGHT_DURATION_US = 16666
    const val LEDS_PATTERN = "-0,-1,-2,-3,-4,c-0,-4,c-0,-3,-4,s-0,-1,-2,-3,-4,c-4,c-4,s-0," +
            "-1,-2,-4,c-2,-4,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,-1,-2,s-0,-1,-2,-3," +
            "-4,c-2,c-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,s-0,-1,-2,-3,-4,c-0,-2,c-0,s-0,-1,-2,-4," +
            "c-2,-4,c-0,-1,-2,-3,-4"
    const val ALBUM_NAME = "v1-Glyphify"

    const val CHANNEL_ID = "Glyphify"

    const val PHONE1_MODEL_ID = "A063"
    val PHONE2_MODEL_ID = listOf("A065", "AIN065")
    const val PHONE2A_MODEL_ID = "A142"
}