# Overworld Maps privacy policy

Last updated 14 November 2026.

Overworld Maps is a navigation app for Android phones and Android Auto, made by Gaurav Kumar (The Algothrim). This page says what the app does with your information. In short: it has no account, no ads and no analytics, and nothing about you is sent to the developer unless you send a problem report yourself.

## Location

The app uses your phone's precise location to show where you are on the map, to plan and follow a route, and to warn you about turns, traffic lights and speed cameras ahead. During a trip it keeps using your location while the phone screen is off, through a navigation notification you can see and stop at any time. It does not use your location when no trip is running and the app isn't open.

While the app is open (on the phone or the car screen), Timeline uses the same location to keep your drives and the places you stop at, so You > Timeline can show them by day and month. Timeline is on when you install the app and stays on your phone (see below); turn it off, delete a day or delete all of it in Settings > Privacy.

## What leaves your phone, and where it goes

To draw the map and plan routes, the app asks these services over encrypted connections (HTTPS). Each one receives the request and, like any website, your phone's internet address. None of them receives your name, account or contacts from the app.

| Service | What the app sends | Why |
|---|---|---|
| Google Maps (unless "Google routes and search" is turned off in Settings) | The start, destination and stops of a trip, and the words you search for, with roughly where you are | Routes, traffic and travel times, and place search |
| Valhalla on the FOSSGIS server (openstreetmap.de) | The route's shape, or the start and destination | Matching the route to the map, planning routes, finding traffic lights and flyovers |
| Photon by komoot | The words you search for, with roughly where you are | Place search |
| Overpass API (overpass-api.de, overpass.kumi.systems) | Small map areas along your route | Speed cameras from OpenStreetMap |
| TomTom | The route, or the area around it | Traffic, incidents and travel times |
| OpenFreeMap | The map tiles for the area on screen, along a planned route, and for any area you download in Settings > Offline | Drawing the map (online and offline), and finding the flyovers and underpasses on and beside the route |
| Terrain Tiles on AWS (s3.amazonaws.com) | Elevation tiles for the area on screen, in the Red Dead world | Drawing its contour lines |
| Photon by komoot, again | Where a Timeline stop or your parked car is | Naming the place (a street, area or city) |

These services handle requests under their own privacy policies. The developer has no access to what they log.

## What stays on your phone

Saved places (Home, Work, the places in your lists, the names and notes you give them, saved trips and where you parked), search history (what you searched for, places you looked at and asked directions to), Timeline (your drives with their lines, and the places you stopped at, with times), downloaded offline maps, speed cameras you mark, your settings, a short drive log used for fixing problems (points in it are rounded to about 100 metres), and a track of the last few hours of driving (each GPS fix as it came, with its speed, heading and height, and the height from the phone's air-pressure sensor where it has one) are kept only on your phone. The app never uploads them. Uninstalling the app, or clearing its data in Android's settings, deletes them. Settings > Privacy can also turn Timeline and search history off, delete them, keep Timeline for 3, 18 or 36 months only, and forget Home, Work, recent places and marked cameras.

The only notifications the app makes itself (besides the trip's own) are a reminder when your parked car's time limit is nearly up, a monthly Timeline recap, and an offline map finishing; each can be turned off in Settings > Alerts. They are made on the phone; nothing is sent to make them.

## Reporting a problem

Settings, About, Report a problem opens your own email app with a message to gaurav@thealgothrim.com and three files attached: a summary of your settings and phone model, the drive log, and the track of the last few hours. Nothing is sent unless you press Send, and you can remove the track (where you drove) from the email first. Reports are used only to find and fix problems in the app. The summary includes a count of how many times this phone asked TomTom for traffic or search in the last month; the count is kept on the phone and goes nowhere else.

## Supporting the app

The app is free and nothing in it is sold. "Support Overworld" in Settings opens a page on the app's website in your browser, where a one-time payment can be made if you choose. The app takes no payment itself and learns nothing about one: it only remembers, on the phone, how many trips you have finished, so that it can ask once, and whether you answered. What the website does with a payment is on its own privacy page.

## Children

The app is not made for children and does not knowingly collect anything from them.

## Changes and contact

If this policy changes, the new version will be posted here with a new date. Questions: gaurav@thealgothrim.com.
