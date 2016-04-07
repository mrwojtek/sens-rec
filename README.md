# Sensors Record

This project is an Android application targeting Android 4.0 (Ice Cream Sandwitch) or above and can be found on [Google Play Store](https://play.google.com/store/apps/details?id=pl.mrwojtek.sensrec.app). It has support for recording
 * GPS location,
 * GPS raw NMEA messages,
 * phone battery state,
 * Bluetooth Low Energy (BLE, Bluetooth 4.0) heart rate sensors,
 * [Android sensors](http://developer.android.com/guide/topics/sensors/sensors_overview.html) like accelerometer, gyroscope, pressure, light, etc.

The data above can be saved to the file or over the network using UDP or TCP protocols. There are two recording formats allowed: a text format with a single event per line or a custom binary format. This project is actually a data acquisition tool for a sibling project [press-alt](https://github.com/mrwojtek/press-alt) which includes parser for a binary format.

## Usage

This software is targeted mostly for developers or users that want to manually process and analyze sensors data.

One example could be a calculation of corrected altitude from fusion of GPS and pressure measurements. Install this app from [Google Play Store](https://play.google.com/store/apps/details?id=pl.mrwojtek.sensrec.app), start recording and share the recorded file to your desktop (e.g. using Dropbox). It is then possible to use scripts from [press-alt](https://github.com/mrwojtek/press-alt) project. To plot GPS altitude and filtered altitude on the left axis and heart rate measurements on the right axis one could run:
```bash
$ ./analyze.py "Recording 1.bin" -1 alt_gps alt_filt -2 heart_rate
```

## License

This project is a free Open Source software release under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
