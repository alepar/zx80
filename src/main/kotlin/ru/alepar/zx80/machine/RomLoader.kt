package ru.alepar.zx80.machine

/**
 * Loads the Sinclair 48K ROM image from the classpath. The ROM is downloaded at build time by
 * the Gradle `downloadRom` task and bundled as a resource at `/rom/48.rom`.
 */
object RomLoader {
    private const val RESOURCE_PATH = "/rom/48.rom"
    private const val EXPECTED_SIZE = 16_384

    fun load48k(): ByteArray {
        val stream = RomLoader::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error(
                "$RESOURCE_PATH not found on classpath. Run ./gradlew downloadRom to fetch the " +
                    "Sinclair 48K ROM, or place a verified copy at " +
                    "build/generated-resources/rom/48.rom."
            )
        val bytes = stream.use { it.readBytes() }
        check(bytes.size == EXPECTED_SIZE) {
            "$RESOURCE_PATH expected $EXPECTED_SIZE bytes, got ${bytes.size}"
        }
        return bytes
    }
}
