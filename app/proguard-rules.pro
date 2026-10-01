# Room creates generated databases by reflection. Its transitive 2.6.1 rule
# keeps the class name, but R8 full mode also needs the no-argument constructor.
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
