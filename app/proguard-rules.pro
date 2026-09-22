# Glance resolves its widget receivers by class name from the manifest.
-keep class agency.dynamicdata.steps.widget.** { *; }

# Health Connect permission strings are looked up reflectively by the platform.
-keep class androidx.health.connect.client.permission.** { *; }
