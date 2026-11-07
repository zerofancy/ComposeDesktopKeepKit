# Annotation defaults and Jackson's enum-valued metadata are read reflectively.
-keepclassmembers enum com.fasterxml.jackson.annotation.** {
    public static **[] values();
}
