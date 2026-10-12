# OSHI 6.6.5 WMI/PDH and Linux proc parsers use Class.getEnumConstants(),
# including through generic helpers and EnumSet. This reflectively invokes values().
# Preserve only that entry: enum classes/fields can still shrink and be renamed.
# Native interfaces/structures are covered by resolved JNA/JNA Platform adapters.
-keepclassmembers enum oshi.** {
    public static **[] values();
}
