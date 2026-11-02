APP_STL := c++_static
APP_ABI := all

# NDK r27 defaults to 4 KB ELF alignment. Keep the keyboard native library
# compatible with both 4 KB and 16 KB page-size Android devices.
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
