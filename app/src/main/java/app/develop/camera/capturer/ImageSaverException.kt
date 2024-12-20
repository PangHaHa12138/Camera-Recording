package app.develop.camera.capturer

class ImageSaverException(val place: Place, cause: Exception? = null) : Exception(cause) {
    enum class Place {
        IMAGE_EXTRACTION,
        IMAGE_CROPPING,
        EXIF_PARSING,
        FILE_CREATION,
        FILE_WRITE,
        FILE_WRITE_COMPLETION,
    }
}
