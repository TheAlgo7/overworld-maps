package com.thealgothrim.overworld.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * The worlds' pictures in Settings, the Layers sheet and the car remote, drawn by the app itself
 * (his 23 Nov ask): every world over the same famous spot in the phone's country's capital, from
 * the same camera, so the four read as one set and follow each world's current style. Drawn once
 * online, kept in files/world-pictures, drawn again only when a world's style changes. Until then
 * (or offline) the bundled India Gate pictures show.
 */
object WorldPictures {
  /** A famous [spot] in [city] (null: the capital's centre, from Natural Earth). */
  data class Spot(val spot: String?, val city: String, val lat: Double, val lon: Double) {
    val caption: String get() = spot?.let { "$it, $city" } ?: city
  }

  private val _pictures = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
  /** Drawn pictures by world id; a world missing here shows its bundled one. */
  val pictures: StateFlow<Map<String, Bitmap>> = _pictures.asStateFlow()

  private val _spot = MutableStateFlow(Spot("India Gate", "New Delhi", 28.6129, 77.2295))
  /** Where the pictures are taken, for the caption under the worlds. */
  val spot: StateFlow<Spot> = _spot.asStateFlow()

  private val main = Handler(Looper.getMainLooper())
  private var started = false
  private var current: MapSnapshotter? = null

  /** The phone's country: its network, then its SIM, then the language setting; India otherwise. */
  fun country(context: Context): String {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return listOfNotNull(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
        .map { it.uppercase(Locale.ROOT) }
        .firstOrNull { it in SPOTS } ?: "IN"
  }

  /** Loads the kept pictures and draws whichever are missing or out of date. Safe to call often. */
  fun ensure(context: Context) {
    if (started) return
    started = true
    val app = context.applicationContext
    val cc = country(app)
    val spot = SPOTS.getValue(cc)
    _spot.value = spot
    val dir = File(app.filesDir, "world-pictures").apply { mkdirs() }
    val wanted = THEMES.associate { t -> t.id to File(dir, "$cc-${t.id}-z$ZOOM-${styleOf(app, t).hashCode().toUInt().toString(16)}.png") }
    // Old pictures (another country, an older style) go.
    dir.listFiles()?.filter { it !in wanted.values }?.forEach { it.delete() }
    val have = wanted.mapNotNull { (id, f) -> f.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.let { id to it } }.toMap()
    _pictures.value = have
    val todo = THEMES.filter { it.id !in have }
    if (todo.isNotEmpty()) MapLibre.getInstance(app)
    drawNext(app, spot, todo, wanted)
  }

  private fun styleOf(context: Context, t: OverworldTheme) = StyleCache.json(context, t, car = false)

  /** One at a time on the main thread (the snapshotter's rule), each saved as it lands. */
  private fun drawNext(context: Context, spot: Spot, todo: List<OverworldTheme>, files: Map<String, File>) {
    val t = todo.firstOrNull() ?: return
    val rest = todo.drop(1)
    main.post {
      val density = context.resources.displayMetrics.density.coerceIn(2f, 3f)
      val options =
          MapSnapshotter.Options(WIDTH, HEIGHT)
              .withStyleJson(styleOf(context, t))
              .withCameraPosition(CameraPosition.Builder().target(LatLng(spot.lat, spot.lon)).zoom(ZOOM).tilt(TILT).bearing(0.0).build())
              .withPixelRatio(density)
              .withLogo(false)
              .withAttribution(false)
      val snapshotter = MapSnapshotter(context, options)
      current = snapshotter
      snapshotter.start(
          { snapshot ->
            val bitmap = snapshot.bitmap
            runCatching { files.getValue(t.id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            _pictures.value = _pictures.value + (t.id to bitmap)
            current = null
            drawNext(context, spot, rest, files)
          },
          { _ ->
            // Offline or a tile failed: keep the bundled picture and try again next time.
            current = null
            started = false
          },
      )
    }
  }

  // The card's shape (400 x 230) at Settings' width; the camera tilted like a trip, close enough
  // for the landmark's streets and buildings to read.
  private const val WIDTH = 400
  private const val HEIGHT = 230
  private const val ZOOM = 14.7
  private const val TILT = 45.0

  private val SPOTS: Map<String, Spot> =
      mapOf(
    "AD" to Spot(null, "Andorra", 42.50000, 1.51649),
    "AE" to Spot("Sheikh Zayed Mosque", "Abu Dhabi", 24.41280, 54.47500),
    "AF" to Spot(null, "Kabul", 34.51864, 69.18131),
    "AG" to Spot(null, "Saint John's", 17.11804, -61.85003),
    "AL" to Spot(null, "Tirana", 41.32754, 19.81888),
    "AM" to Spot(null, "Yerevan", 40.18310, 44.51161),
    "AO" to Spot(null, "Luanda", -8.83634, 13.23248),
    "AR" to Spot("the Obelisco", "Buenos Aires", -34.60370, -58.38160),
    "AT" to Spot("St. Stephen's Cathedral", "Vienna", 48.20850, 16.37310),
    "AU" to Spot("Parliament House", "Canberra", -35.30810, 149.12450),
    "AZ" to Spot(null, "Baku", 40.39722, 49.86027),
    "BA" to Spot(null, "Sarajevo", 43.85002, 18.38300),
    "BB" to Spot(null, "Bridgetown", 13.10200, -59.61653),
    "BD" to Spot("the Parliament", "Dhaka", 23.76260, 90.37850),
    "BE" to Spot("the Grand-Place", "Brussels", 50.84670, 4.35250),
    "BF" to Spot(null, "Ouagadougou", 12.37226, -1.52667),
    "BG" to Spot(null, "Sofia", 42.68530, 23.31471),
    "BH" to Spot(null, "Manama", 26.23614, 50.58305),
    "BI" to Spot(null, "Bujumbura", -3.37609, 29.36001),
    "BJ" to Spot(null, "Cotonou", 6.40195, 2.51804),
    "BN" to Spot(null, "Bandar Seri Begawan", 4.88333, 114.93328),
    "BO" to Spot(null, "La Paz", -16.49603, -68.15193),
    "BR" to Spot("the National Congress", "Brasília", -15.79970, -47.86450),
    "BS" to Spot(null, "Nassau", 25.08339, -77.35004),
    "BT" to Spot(null, "Thimphu", 27.47299, 89.63901),
    "BW" to Spot(null, "Gaborone", -24.64631, 25.91195),
    "BY" to Spot(null, "Minsk", 53.90192, 27.56468),
    "BZ" to Spot(null, "Belmopan", 17.25203, -88.76707),
    "CA" to Spot("Parliament Hill", "Ottawa", 45.42360, -75.70090),
    "CD" to Spot(null, "Kinshasa", -4.32778, 15.31303),
    "CF" to Spot(null, "Bangui", 4.36664, 18.55829),
    "CG" to Spot(null, "Brazzaville", -4.25724, 15.28274),
    "CH" to Spot("the Federal Palace", "Bern", 46.94660, 7.44400),
    "CI" to Spot(null, "Abidjan", 5.32194, -4.04199),
    "CL" to Spot("Plaza de Armas", "Santiago", -33.43780, -70.65050),
    "CM" to Spot(null, "Yaoundé", 3.86865, 11.51470),
    "CN" to Spot("Tiananmen", "Beijing", 39.90550, 116.39760),
    "CO" to Spot("Plaza de Bolívar", "Bogotá", 4.59810, -74.07600),
    "CR" to Spot(null, "San José", 9.93696, -84.08600),
    "CU" to Spot(null, "Havana", 23.13390, -82.36613),
    "CV" to Spot(null, "Praia", 14.91670, -23.51669),
    "CY" to Spot(null, "Nicosia", 35.16668, 33.36664),
    "CZ" to Spot("Old Town Square", "Prague", 50.08750, 14.42130),
    "DE" to Spot("the Brandenburg Gate", "Berlin", 52.51630, 13.37770),
    "DJ" to Spot(null, "Djibouti", 11.59502, 43.14800),
    "DK" to Spot("Nyhavn", "Copenhagen", 55.67980, 12.59070),
    "DM" to Spot(null, "Roseau", 15.30102, -61.38701),
    "DO" to Spot(null, "Santo Domingo", 18.47202, -69.90203),
    "DZ" to Spot(null, "Algiers", 36.76501, 3.04861),
    "EC" to Spot(null, "Quito", -0.21304, -78.50200),
    "EE" to Spot(null, "Tallinn", 59.43388, 24.72804),
    "EG" to Spot("Tahrir Square", "Cairo", 30.04440, 31.23570),
    "ER" to Spot(null, "Asmara", 15.33334, 38.93332),
    "ES" to Spot("the Royal Palace", "Madrid", 40.41800, -3.71430),
    "ET" to Spot(null, "Addis Ababa", 9.03526, 38.69806),
    "FI" to Spot("Senate Square", "Helsinki", 60.16950, 24.95250),
    "FJ" to Spot(null, "Suva", -18.13302, 178.44171),
    "FM" to Spot(null, "Palikir", 6.91664, 158.14997),
    "FR" to Spot("the Eiffel Tower", "Paris", 48.85840, 2.29450),
    "GA" to Spot(null, "Libreville", 0.38539, 9.45796),
    "GB" to Spot("Westminster", "London", 51.50070, -0.12460),
    "GD" to Spot(null, "Saint George's", 12.05263, -61.74164),
    "GE" to Spot(null, "Tbilisi", 41.72696, 44.78885),
    "GH" to Spot(null, "Accra", 5.55198, -0.21866),
    "GM" to Spot(null, "Banjul", 13.45388, -16.59170),
    "GN" to Spot(null, "Conakry", 9.53347, -13.68218),
    "GQ" to Spot(null, "Malabo", 3.75001, 8.78328),
    "GR" to Spot("the Acropolis", "Athens", 37.97150, 23.72570),
    "GT" to Spot(null, "Guatemala City", 14.62308, -90.52891),
    "GW" to Spot(null, "Bissau", 11.86502, -15.59836),
    "GY" to Spot(null, "Georgetown", 6.80197, -58.16703),
    "HN" to Spot(null, "Tegucigalpa", 14.10399, -87.21948),
    "HR" to Spot(null, "Zagreb", 45.80001, 16.00000),
    "HT" to Spot(null, "Port-au-Prince", 18.54297, -72.33798),
    "HU" to Spot("the Parliament", "Budapest", 47.50710, 19.04560),
    "ID" to Spot("Monas", "Jakarta", -6.17540, 106.82720),
    "IE" to Spot("O'Connell Street", "Dublin", 53.34980, -6.26030),
    "IL" to Spot(null, "Jerusalem", 31.77841, 35.20663),
    "IN" to Spot("India Gate", "New Delhi", 28.61290, 77.22950),
    "IQ" to Spot(null, "Baghdad", 33.34059, 44.39192),
    "IR" to Spot("Azadi Tower", "Tehran", 35.69970, 51.33800),
    "IS" to Spot("Hallgrímskirkja", "Reykjavík", 64.14170, -21.92660),
    "IT" to Spot("the Colosseum", "Rome", 41.89020, 12.49220),
    "JM" to Spot(null, "Kingston", 17.97708, -76.76743),
    "JO" to Spot(null, "Amman", 31.95197, 35.93135),
    "JP" to Spot("the Imperial Palace", "Tokyo", 35.68520, 139.75280),
    "KE" to Spot("the KICC", "Nairobi", -1.28840, 36.82330),
    "KG" to Spot(null, "Bishkek", 42.87503, 74.58326),
    "KH" to Spot(null, "Phnom Penh", 11.55198, 104.91469),
    "KI" to Spot(null, "Tarawa", 1.33819, 173.01757),
    "KM" to Spot(null, "Moroni", -11.70416, 43.24024),
    "KN" to Spot(null, "Basseterre", 17.30203, -62.71701),
    "KP" to Spot(null, "Pyongyang", 39.02139, 125.75275),
    "KR" to Spot("Gyeongbokgung", "Seoul", 37.57960, 126.97700),
    "KW" to Spot("Kuwait Towers", "Kuwait City", 29.38970, 48.00360),
    "KZ" to Spot(null, "Nur-Sultan", 51.18113, 71.42777),
    "LA" to Spot(null, "Vientiane", 17.96669, 102.59998),
    "LB" to Spot(null, "Beirut", 33.87392, 35.50776),
    "LC" to Spot(null, "Castries", 14.00197, -61.00001),
    "LI" to Spot(null, "Vaduz", 47.13372, 9.51667),
    "LK" to Spot(null, "Colombo", 6.93197, 79.85775),
    "LR" to Spot(null, "Monrovia", 6.31458, -10.79966),
    "LS" to Spot(null, "Maseru", -29.31667, 27.48327),
    "LT" to Spot(null, "Vilnius", 54.68337, 25.31664),
    "LU" to Spot(null, "Luxembourg", 49.61166, 6.13000),
    "LV" to Spot(null, "Riga", 56.95002, 24.09997),
    "LY" to Spot(null, "Tripoli", 32.89250, 13.18001),
    "MA" to Spot(null, "Rabat", 34.02531, -6.83641),
    "MC" to Spot(null, "Monaco", 43.73965, 7.40691),
    "MD" to Spot(null, "Chișinău", 47.00502, 28.85771),
    "ME" to Spot(null, "Podgorica", 42.46597, 19.26631),
    "MG" to Spot(null, "Antananarivo", -18.91469, 47.51468),
    "MH" to Spot(null, "Majuro", 7.10300, 171.38000),
    "MK" to Spot(null, "Skopje", 42.00001, 21.43346),
    "ML" to Spot(null, "Bamako", 12.65196, -8.00198),
    "MM" to Spot(null, "Yangon", 16.78530, 96.16473),
    "MN" to Spot(null, "Ulaanbaatar", 47.91862, 106.91467),
    "MR" to Spot(null, "Nouakchott", 18.08643, -15.97534),
    "MT" to Spot(null, "Valletta", 35.89973, 14.51471),
    "MU" to Spot(null, "Port Louis", -20.16664, 57.49999),
    "MV" to Spot(null, "Malé", 4.16671, 73.49995),
    "MW" to Spot(null, "Lilongwe", -13.98330, 33.78330),
    "MX" to Spot("the Zócalo", "Mexico City", 19.43260, -99.13320),
    "MY" to Spot("the Petronas Towers", "Kuala Lumpur", 3.15790, 101.71160),
    "MZ" to Spot(null, "Maputo", -25.95333, 32.58722),
    "NA" to Spot(null, "Windhoek", -22.57001, 17.08355),
    "NE" to Spot(null, "Niamey", 13.51865, 2.11471),
    "NG" to Spot(null, "Abuja", 9.08528, 7.53138),
    "NI" to Spot(null, "Managua", 12.15496, -86.27044),
    "NL" to Spot("Dam Square", "Amsterdam", 52.37310, 4.89260),
    "NO" to Spot("the Opera House", "Oslo", 59.90750, 10.75310),
    "NP" to Spot("Durbar Square", "Kathmandu", 27.70430, 85.30700),
    "NZ" to Spot("the Beehive", "Wellington", -41.27840, 174.77670),
    "OM" to Spot("the Grand Mosque", "Muscat", 23.58380, 58.38890),
    "PA" to Spot(null, "Panama City", 8.96996, -79.53498),
    "PE" to Spot("Plaza Mayor", "Lima", -12.04640, -77.03000),
    "PG" to Spot(null, "Port Moresby", -9.46471, 147.19250),
    "PH" to Spot("Rizal Park", "Manila", 14.58260, 120.97870),
    "PK" to Spot("Faisal Mosque", "Islamabad", 33.72950, 73.03720),
    "PL" to Spot("the Palace of Culture", "Warsaw", 52.23170, 21.00600),
    "PT" to Spot("Praça do Comércio", "Lisbon", 38.70750, -9.13640),
    "PW" to Spot(null, "Melekeok", 7.48740, 134.62655),
    "PY" to Spot(null, "Asunción", -25.29446, -57.64345),
    "QA" to Spot("the Corniche", "Doha", 25.29550, 51.53900),
    "RO" to Spot(null, "Bucharest", 44.43532, 26.09800),
    "RS" to Spot(null, "Belgrade", 44.82059, 20.46605),
    "RU" to Spot("Red Square", "Moscow", 55.75390, 37.62080),
    "RW" to Spot(null, "Kigali", -1.95164, 30.05859),
    "SA" to Spot("Kingdom Centre", "Riyadh", 24.71140, 46.67440),
    "SB" to Spot(null, "Honiara", -9.43799, 159.94977),
    "SC" to Spot(null, "Victoria", -4.61663, 55.44999),
    "SD" to Spot(null, "Khartoum", 15.59002, 32.53223),
    "SE" to Spot("Gamla Stan", "Stockholm", 59.32680, 18.07170),
    "SG" to Spot("Marina Bay", "Singapore", 1.28340, 103.86070),
    "SI" to Spot(null, "Ljubljana", 46.05529, 14.51497),
    "SK" to Spot(null, "Bratislava", 48.15002, 17.11698),
    "SL" to Spot(null, "Freetown", 8.47196, -13.23616),
    "SM" to Spot(null, "San Marino", 43.93610, 12.44177),
    "SN" to Spot(null, "Dakar", 14.71778, -17.47508),
    "SO" to Spot(null, "Mogadishu", 2.06863, 45.36473),
    "SR" to Spot(null, "Paramaribo", 5.83503, -55.16703),
    "SS" to Spot(null, "Juba", 4.82998, 31.58003),
    "ST" to Spot(null, "São Tomé", 0.33340, 6.73332),
    "SV" to Spot(null, "San Salvador", 13.71195, -89.20499),
    "SY" to Spot(null, "Damascus", 33.50198, 36.29805),
    "SZ" to Spot(null, "Mbabane", -26.31665, 31.13333),
    "TD" to Spot(null, "N'Djamena", 12.11504, 15.04720),
    "TG" to Spot(null, "Lomé", 6.13388, 1.22081),
    "TH" to Spot("the Grand Palace", "Bangkok", 13.75000, 100.49130),
    "TJ" to Spot(null, "Dushanbe", 38.56003, 68.77388),
    "TL" to Spot(null, "Dili", -8.55939, 125.57946),
    "TM" to Spot(null, "Ashgabat", 37.95000, 58.38330),
    "TN" to Spot(null, "Tunis", 36.80278, 10.17968),
    "TO" to Spot(null, "Nuku'alofa", -21.13851, -175.22056),
    "TR" to Spot("Anıtkabir", "Ankara", 39.92500, 32.83690),
    "TT" to Spot(null, "Port-of-Spain", 10.65200, -61.51703),
    "TV" to Spot(null, "Funafuti", -8.51665, 179.21665),
    "TW" to Spot(null, "Taipei", 25.03583, 121.56833),
    "TZ" to Spot(null, "Dar es Salaam", -6.79807, 39.26640),
    "UA" to Spot("Maidan", "Kyiv", 50.45010, 30.52340),
    "UG" to Spot(null, "Kampala", 0.31861, 32.58138),
    "US" to Spot("the Capitol", "Washington", 38.88990, -77.00910),
    "UY" to Spot(null, "Montevideo", -34.85610, -56.17300),
    "UZ" to Spot(null, "Tashkent", 41.31365, 69.29299),
    "VA" to Spot(null, "Vatican City", 41.90328, 12.45339),
    "VC" to Spot(null, "Kingstown", 13.14828, -61.21206),
    "VE" to Spot(null, "Caracas", 10.50294, -66.91898),
    "VN" to Spot("Hoan Kiem Lake", "Hanoi", 21.02870, 105.85240),
    "VU" to Spot(null, "Port Vila", -17.73335, 168.31664),
    "WS" to Spot(null, "Apia", -13.84154, -171.73864),
    "YE" to Spot(null, "Sanaa", 15.35668, 44.20465),
    "ZA" to Spot("the Union Buildings", "Pretoria", -25.74000, 28.21220),
    "ZM" to Spot(null, "Lusaka", -15.41470, 28.28138),
    "ZW" to Spot(null, "Harare", -17.81584, 31.04276),
      )
}
