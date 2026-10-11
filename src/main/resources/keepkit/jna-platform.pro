# LibCAPI is a native-symbol superinterface but does not itself extend Library.
# Its methods are invoked through platform.linux.LibC / platform.unix.LibC.
-keepclassmembers,allowshrinking interface com.sun.jna.platform.unix.LibCAPI {
    public <methods>;
}
