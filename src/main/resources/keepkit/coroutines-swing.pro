# Swing Main dispatcher is discovered from META-INF/services.
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory {
    public <init>();
}
