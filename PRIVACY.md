# Overworld Maps privacy policy

Last updated 16 October 2026.

Overworld Maps is a navigation app for Android phones and Android Auto, made by Gaurav Kumar (The Algothrim). This page says what the app does with your information. In short: it has no account, no ads and no analytics, and nothing about you is sent to the developer.

## Location

The app uses your phone's precise location to show where you are on the map, to plan and follow a route, and to warn you about turns, traffic lights and speed cameras ahead. During a trip it keeps using your location while the phone screen is off, through a navigation notification you can see and stop at any time. It does not use your location when no trip is running and the app isn't open.

## What leaves your phone, and where it goes

To draw the map and plan routes, the app asks these services over encrypted connections (HTTPS). Each one receives the request and, like any website, your phone's internet address. None of them receives your name, account or contacts from the app.

| Service | What the app sends | Why |
|---|---|---|
| Google Maps (unless "Google routes and search" is turned off in Settings) | The start, destination and stops of a trip, and the words you search for, with roughly where you are | Routes, traffic and travel times, and place search |
| Valhalla on the FOSSGIS server (openstreetmap.de) | The route's shape, or the start and destination | Matching the route to the map, planning routes, finding traffic lights and flyovers |
| Photon by komoot | The words you search for, with roughly where you are | Place search |
| Overpass API (overpass-api.de, overpass.kumi.systems) | Small map areas along your route | Speed cameras from OpenStreetMap |
| TomTom | The route, or the area around it | Traffic, incidents and travel times |
| OpenFreeMap | The map tiles for the area on screen | Drawing the map |

These services handle requests under their own privacy policies. The developer has no access to what they log.

## What stays on your phone

Saved places (Home, Work and the places you save), speed cameras you mark, your settings, and a short drive log used for fixing problems (points in it are rounded to about 100 metres) are kept only on your phone. They are never uploaded. Uninstalling the app, or clearing its data in Android's settings, deletes them. Settings can also forget Home, Work, recent places and marked cameras.

## Children

The app is not made for children and does not knowingly collect anything from them.

## Changes and contact

If this policy changes, the new version will be posted here with a new date. Questions: gaurav@thealgothrim.com.
