package com.example.tasama.presentation.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.RoutePoint
import com.example.tasama.domain.model.User
import com.example.tasama.domain.model.WeatherInfo
import com.example.tasama.domain.repository.DistanceInfo

import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import com.example.tasama.util.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.*
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.darwin.NSObject
import platform.Foundation.NSSelectorFromString
import kotlin.math.PI
import kotlin.math.atan2
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.*
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import androidx.compose.ui.graphics.drawscope.rotate
import coil3.compose.AsyncImage

val MapHeaderHeight = 88.dp

@OptIn(ExperimentalForeignApi::class)
class MapCoordinator(
    private val onMapClick: (Double, Double) -> Unit,
    private val onRegionChanged: (MKMapView) -> Unit
) : NSObject(), MKMapViewDelegateProtocol {
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun handleTap(gesture: platform.UIKit.UITapGestureRecognizer) {
        val mapView = gesture.view as? MKMapView ?: return
        val touchPoint = gesture.locationInView(mapView)
        val coordinate = mapView.convertPoint(touchPoint, toCoordinateFromView = mapView)
        coordinate.useContents {
            onMapClick(latitude, longitude)
        }
    }

    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        onRegionChanged(mapView)
    }

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        return when (val overlay = rendererForOverlay) {
            is MKPolyline -> {
                MKPolylineRenderer(overlay).apply {
                    val style = overlay.title
                    when (style) {
                        "Dashed" -> {
                            strokeColor = UIColor.systemBlueColor
                            lineWidth = 4.0
                            lineDashPattern = listOf(10, 10)
                        }
                        else -> {
                            strokeColor = UIColor.systemBlueColor
                            lineWidth = 6.0
                        }
                    }
                }
            }
            is MKCircle -> {
                MKCircleRenderer(overlay).apply {
                    fillColor = (overlay.title?.let { 
                        try { UIColor.fromHex(it).colorWithAlphaComponent(0.2) } 
                        catch(e: Exception) { UIColor.systemBlueColor.colorWithAlphaComponent(0.2) }
                    } ?: UIColor.systemBlueColor.colorWithAlphaComponent(0.2)) as UIColor
                    strokeColor = (overlay.title?.let { 
                        try { UIColor.fromHex(it) } 
                        catch(e: Exception) { UIColor.systemBlueColor }
                    } ?: UIColor.systemBlueColor) as UIColor
                    lineWidth = 2.0
                }
            }
            else -> MKOverlayRenderer(overlay)
        }
    }
}

private fun UIColor.Companion.fromHex(hex: String): UIColor {
    val cleanHex = if (hex.startsWith("#")) hex.substring(1) else hex
    val longVal = try { cleanHex.toLong(16) } catch(e: Exception) { 0L }
    val r = (longVal shr 16 and 0xFF).toDouble() / 255.0
    val g = (longVal shr 8 and 0xFF).toDouble() / 255.0
    val b = (longVal and 0xFF).toDouble() / 255.0
    val a = if (cleanHex.length == 8) (longVal shr 24 and 0xFF).toDouble() / 255.0 else 1.0
    return UIColor(red = r, green = g, blue = b, alpha = a)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalForeignApi::class)
@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    anniversaryDate: Long?,
    distanceInfo: DistanceInfo?,
    weatherInfo: WeatherInfo?,
    isWeatherLoading: Boolean,
    isPartnerComingToMe: Boolean,
    isDistanceLoading: Boolean,
    distanceError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onUnlink: () -> Unit,
    settings: com.example.tasama.domain.model.AppSettings,
    onOpenSettings: () -> Unit
) {
    var mapViewInstance by remember { mutableStateOf<MKMapView?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    var showAddPlaceSheet by remember { mutableStateOf<Location?>(null) }
    var isPlacementModeEnabled by rememberSaveable { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    
    var currentTime by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTime = Clock.System.now().toEpochMilliseconds()
        }
    }

    val myLocation = currentUser?.let { Location(it.latitude ?: 0.0, it.longitude ?: 0.0) }
    val partnerLocation = partner?.let { Location(it.latitude ?: 0.0, it.longitude ?: 0.0) }

    val coordinator = remember {
        MapCoordinator(
            onMapClick = { lat, lon ->
                if (isPlacementModeEnabled) return@MapCoordinator
                isPartnerInfoVisible = false
                
                val clickedPlace = places.find { place ->
                    calculateDistance(Location(lat, lon), Location(place.latitude, place.longitude)) <= maxOf(place.radius, 50.0)
                }
                if (clickedPlace != null) {
                    editingPlace = clickedPlace
                    showAddPlaceSheet = Location(clickedPlace.latitude, clickedPlace.longitude)
                }
            },
            onRegionChanged = { /* Handle if needed */ }
        )
    }

    fun fitAllMarkers() {
        val mv = mapViewInstance ?: return
        val points = mutableListOf<Location>()
        myLocation?.let { points.add(it) }
        partnerLocation?.let { points.add(it) }
        
        if (points.isEmpty()) return

        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0

        points.forEach {
            if (it.latitude < minLat) minLat = it.latitude
            if (it.latitude > maxLat) maxLat = it.latitude
            if (it.longitude < minLon) minLon = it.longitude
            if (it.longitude > maxLon) maxLon = it.longitude
        }

        val center = CLLocationCoordinate2DMake((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
        val span = MKCoordinateSpanMake(maxOf((maxLat - minLat) * 1.5, 0.01), maxOf((maxLon - minLon) * 1.5, 0.01))
        mv.setRegion(MKCoordinateRegionMake(center, span), animated = true)
    }

    val isTogether = remember(myLocation, partnerLocation) {
        if (myLocation == null || partnerLocation == null) false
        else calculateDistance(myLocation, partnerLocation) < 25.0
    }

    val markerData = remember(myLocation, partnerLocation, isTogether, mapSize, mapViewInstance) {
        mapViewInstance?.let { mapView ->
            val pMe = myLocation?.let { loc ->
                val coord = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                mapView.convertCoordinate(coord, toPointToView = mapView).useContents {
                    Offset(x.toFloat() * density.density, y.toFloat() * density.density)
                }
            }
            val pPartner = partnerLocation?.let { loc ->
                val coord = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                mapView.convertCoordinate(coord, toPointToView = mapView).useContents {
                    Offset(x.toFloat() * density.density, y.toFloat() * density.density)
                }
            }

            val isMeVisible = pMe?.let { it.x in 0f..mapSize.width.toFloat() && it.y in 0f..mapSize.height.toFloat() } ?: false
            val isPartnerVisible = pPartner?.let { it.x in 0f..mapSize.width.toFloat() && it.y in 0f..mapSize.height.toFloat() } ?: false
            
            val showPolyline = !isTogether && myLocation != null && partnerLocation != null

            var myEdge: Offset? = null
            var myAngle = 0f
            if (!isMeVisible && pMe != null && mapSize.width > 0) {
                val center = Offset(mapSize.width / 2f, mapSize.height / 2f)
                myEdge = findRayIntersection(center, pMe, mapSize.width.toFloat(), mapSize.height.toFloat())
                if (myEdge != null) {
                    val dx = pMe.x - myEdge.x
                    val dy = pMe.y - myEdge.y
                    myAngle = (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI).toFloat()
                }
            }

            var partnerEdge: Offset? = null
            var partnerAngle = 0f
            if (!isPartnerVisible && pPartner != null && mapSize.width > 0) {
                val center = Offset(mapSize.width / 2f, mapSize.height / 2f)
                partnerEdge = findRayIntersection(center, pPartner, mapSize.width.toFloat(), mapSize.height.toFloat())
                if (partnerEdge != null) {
                    val dx = pPartner.x - partnerEdge.x
                    val dy = pPartner.y - partnerEdge.y
                    partnerAngle = (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI).toFloat()
                }
            }

            MapMarkerVisibilityData(
                isMeVisible = isMeVisible,
                isPartnerVisible = isPartnerVisible,
                myEffectiveLocation = myLocation ?: Location(0.0, 0.0),
                partnerEffectiveLocation = partnerLocation ?: Location(0.0, 0.0),
                myEdgePoint = myEdge,
                partnerEdgePoint = partnerEdge,
                myAngle = myAngle,
                partnerAngle = partnerAngle,
                showPolyline = showPolyline,
                partnerScreenPos = pPartner
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { mapSize = it }) {
        UIKitView(
            factory = {
                MKMapView().apply {
                    delegate = coordinator
                    rotateEnabled = true
                    pitchEnabled = true
                    showsUserLocation = false
                    
                    val tapGesture = platform.UIKit.UITapGestureRecognizer(coordinator, NSSelectorFromString("handleTap:"))
                    addGestureRecognizer(tapGesture)
                    
                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                mapView.removeOverlays(mapView.overlays)
                mapView.showsTraffic = settings.trafficLayerEnabled
                
                if (settings.partnerMapEnabled && settings.placesEnabled) {
                    places.forEach { place ->
                        val coord = CLLocationCoordinate2DMake(place.latitude, place.longitude)
                        val circle = MKCircle.circleWithCenterCoordinate(coord, place.radius)
                        circle.setTitle(place.color?.toString() ?: "")
                        mapView.addOverlay(circle)
                    }
                }

                markerData?.let { data ->
                    if (settings.partnerMapEnabled && data.showPolyline) {
                        val coords = listOf(
                            CLLocationCoordinate2DMake(data.myEffectiveLocation.latitude, data.myEffectiveLocation.longitude),
                            CLLocationCoordinate2DMake(data.partnerEffectiveLocation.latitude, data.partnerEffectiveLocation.longitude)
                        )
                        memScoped {
                            val nativeCoords = allocArray<CLLocationCoordinate2D>(coords.size)
                            coords.forEachIndexed { index, coord ->
                                nativeCoords[index].latitude = coord.useContents { latitude }
                                nativeCoords[index].longitude = coord.useContents { longitude }
                            }
                            val polyline = MKPolyline.polylineWithCoordinates(nativeCoords, coords.size.toULong())
                            polyline.setTitle("Dashed")
                            mapView.addOverlay(polyline)
                        }
                    }
                }

                mapView.removeAnnotations(mapView.annotations)
                val annotations = mutableListOf<platform.MapKit.MKPointAnnotation>()
                
                if (settings.partnerMapEnabled) {
                    if (settings.placesEnabled) {
                        annotations.addAll(places.map { place ->
                            MKPointAnnotation().apply {
                                setCoordinate(CLLocationCoordinate2DMake(place.latitude, place.longitude))
                                setTitle(place.name)
                            }
                        })
                    }
                }
                mapView.addAnnotations(annotations)
            }
        )

        markerData?.let { data ->
            if (settings.partnerMapEnabled && isTogether && myLocation != null && partnerLocation != null) {
                val midpoint = Location(
                    (myLocation.latitude + partnerLocation.latitude) / 2.0,
                    (myLocation.longitude + partnerLocation.longitude) / 2.0
                )
                val coord = CLLocationCoordinate2DMake(midpoint.latitude, midpoint.longitude)
                val pos = mapViewInstance?.convertCoordinate(coord, toPointToView = mapViewInstance)?.useContents {
                    Offset(x.toFloat() * density.density, y.toFloat() * density.density)
                }
                
                if (pos != null && pos.x in 0f..mapSize.width.toFloat() && pos.y in 0f..mapSize.height.toFloat()) {
                    Box(modifier = Modifier.offset { 
                        IntOffset(pos.x.toInt() - with(density) { 50.dp.toPx().toInt() }, pos.y.toInt() - with(density) { 55.dp.toPx().toInt() }) 
                    }) {
                        val statusMe = rememberPartnerStatus(currentUser, currentTime)
                        val statusPartner = rememberPartnerStatus(partner, currentTime)
                        CombinedUserMarker(
                            currentUser = currentUser,
                            partner = partner,
                            statusMe = statusMe,
                            statusPartner = statusPartner
                        )
                    }
                }
            }

            if (data.isMeVisible && (!isTogether || !settings.partnerMapEnabled)) {
                val pos = myLocation?.let { loc ->
                    val coord = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                    mapViewInstance?.convertCoordinate(coord, toPointToView = mapViewInstance)?.useContents {
                        Offset(x.toFloat() * density.density, y.toFloat() * density.density)
                    }
                }
                pos?.let {
                    Box(modifier = Modifier.offset { IntOffset(it.x.toInt() - 32.dp.toPx().toInt(), it.y.toInt() - 48.dp.toPx().toInt()) }) {
                        UserMarker(currentUser, true, ConnectionStatus.LIVE)
                    }
                }
            } else if (data.myEdgePoint != null && (!isTogether || !settings.partnerMapEnabled)) {
                OffScreenMarker(edgePoint = data.myEdgePoint, angle = data.myAngle, user = currentUser, onTap = { fitAllMarkers() })
            }

            if (settings.partnerMapEnabled && data.isPartnerVisible && !isTogether) {
                data.partnerScreenPos?.let {
                    Box(modifier = Modifier.offset { IntOffset(it.x.toInt() - 32.dp.toPx().toInt(), it.y.toInt() - 48.dp.toPx().toInt()) }) {
                        UserMarker(partner, false, rememberPartnerStatus(partner, currentTime), onClick = { 
                            if (!isPlacementModeEnabled) isPartnerInfoVisible = !isPartnerInfoVisible 
                        })
                    }
                }
            } else if (settings.partnerMapEnabled && data.partnerEdgePoint != null && !isTogether) {
                OffScreenMarker(edgePoint = data.partnerEdgePoint, angle = data.partnerAngle, user = partner, onTap = { fitAllMarkers() })
            }
        }

        if (settings.partnerMapEnabled && partner != null && isPartnerInfoVisible && !isPlacementModeEnabled) {
            val status = rememberPartnerStatus(partner, currentTime)
            PartnerStatusCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .padding(horizontal = 16.dp),
                user = partner,
                status = status,
                currentTime = currentTime,
                distanceInfo = distanceInfo,
                isPartnerComingToMe = isPartnerComingToMe,
                isDistanceLoading = isDistanceLoading,
                distanceError = distanceError
            )
        }

        // New Header Layout: Weather(Left), Dashboard(Center), Settings(Right)
        AnimatedVisibility(
            visible = !isPlacementModeEnabled,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 14.dp)
            ) {
                if (settings.partnerMapEnabled && settings.weatherWidgetEnabled) {
                    WeatherWidget(
                        modifier = Modifier.align(Alignment.CenterStart),
                        weatherInfo = weatherInfo,
                        isLoading = isWeatherLoading
                    )
                }

                if (settings.partnerMapEnabled && settings.dashboardEnabled) {
                    PartnerDashboard(
                        modifier = Modifier.align(Alignment.Center),
                        anniversaryDate = anniversaryDate,
                        currentTime = currentTime,
                        onEditAnniversary = onEditAnniversary
                    )
                }

                // Map Settings Button (Top Right, standalone circular button)
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(40.dp)
                        .clickable { onOpenSettings() },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Placement Mode UI
        if (isPlacementModeEnabled) {
            // Center Pin / Crosshair
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Pin icon
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .offset(y = (-22).dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                // Small dot at the exact center for precision
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            // Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Move map to set location",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { isPlacementModeEnabled = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            mapViewInstance?.centerCoordinate?.useContents {
                                showAddPlaceSheet = Location(latitude, longitude)
                            }
                            isPlacementModeEnabled = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Confirm Location")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isPlacementModeEnabled,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = { fitAllMarkers() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit Markers")
                }

                SmallFloatingActionButton(
                    onClick = { isPlacementModeEnabled = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Place")
                }
            }
        }

        if (showAddPlaceSheet != null) {
            val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddPlaceSheet = null
                    editingPlace = null
                },
                sheetState = bottomSheetState
            ) {
                AddPlaceSheetContent(
                    location = showAddPlaceSheet!!,
                    initialPlace = editingPlace,
                    onAddPlace = { place: Place ->
                        onAddPlace(place)
                        showAddPlaceSheet = null
                        editingPlace = null
                    }
                )
            }
        }
    }
}

@Composable
fun AddPlaceSheetContent(
    location: Location,
    initialPlace: Place? = null,
    onAddPlace: (Place) -> Unit
) {
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var address by remember { mutableStateOf(initialPlace?.address ?: "Fetching address...") }
    val radius = initialPlace?.radius?.toFloat() ?: 200f
    var notifyOnEntry by remember { mutableStateOf(initialPlace?.notifyOnEntry ?: true) }
    var notifyOnExit by remember { mutableStateOf(initialPlace?.notifyOnExit ?: true) }
    var selectedColor by remember { 
        mutableStateOf(initialPlace?.color?.let { Color(it.toULong()) } ?: Color(0xFF2196F3)) 
    }
    var selectedIconName by remember { mutableStateOf(initialPlace?.iconName ?: "Location") }

    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFF44336), // Red
        Color(0xFFFFC107), // Amber
        Color(0xFF9C27B0), // Purple
        Color(0xFF795548)  // Brown
    )

    val icons = listOf(
        "Location" to Icons.Default.LocationOn,
        "Home" to Icons.Default.Home,
        "Work" to Icons.Default.Work,
        "School" to Icons.Default.School,
        "Shopping" to Icons.Default.ShoppingCart,
        "Restaurant" to Icons.Default.Restaurant,
        "Gym" to Icons.Default.FitnessCenter,
        "Hospital" to Icons.Default.LocalHospital,
        "Park" to Icons.Default.Park
    )

    LaunchedEffect(location, initialPlace) {
        if (initialPlace != null) return@LaunchedEffect
        
        delay(500) // Debounce rapid location changes during interaction
        
        val result = reverseGeocode(location.latitude, location.longitude)
        if (result != null) {
            address = result
            if (name.isEmpty()) {
                name = result.split(",").firstOrNull() ?: result
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (initialPlace == null) "Add Place Marker" else "Edit Place Marker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Get notified when your partner enters or leaves this area.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Place Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NotificationChip(
                label = "Entry",
                selected = notifyOnEntry,
                onClick = { notifyOnEntry = !notifyOnEntry },
                modifier = Modifier.weight(1f)
            )
            NotificationChip(
                label = "Exit",
                selected = notifyOnExit,
                onClick = { notifyOnExit = !notifyOnExit },
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(if (isSelected) 4.dp else 0.dp)
                                .background(color, CircleShape)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icons.forEach { (iconName, icon) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedIconName == iconName) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { selectedIconName = iconName },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconName,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedIconName == iconName) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                onAddPlace(
                    Place(
                        id = initialPlace?.id ?: "",
                        name = name.ifBlank { address },
                        address = address,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radius = radius.toDouble(),
                        notifyOnEntry = notifyOnEntry,
                        notifyOnExit = notifyOnExit,
                        color = selectedColor.toArgb().toLong(),
                        iconName = selectedIconName
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() || address != "Fetching address...",
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (initialPlace == null) "Save Place" else "Update Place")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun NotificationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    )
}

fun Color.toArgb(): Int {
    return ((alpha * 255.0f + 0.5f).toInt() shl 24) or
            ((red * 255.0f + 0.5f).toInt() shl 16) or
            ((green * 255.0f + 0.5f).toInt() shl 8) or
            (blue * 255.0f + 0.5f).toInt()
}

@Composable
fun UserMarker(user: User?, isMe: Boolean, status: ConnectionStatus, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() }
            ),
        contentAlignment = Alignment.Center
    ) {
        val pulseScale by rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        )

        if (status == ConnectionStatus.LIVE) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            )
        }

        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            border = BorderStroke(2.dp, if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
            shadowElevation = 4.dp
        ) {
            UserAvatar(user = user, modifier = Modifier.fillMaxSize(), showInitials = user?.avatarUrl == null)
        }
    }
}

@Composable
fun CombinedUserMarker(
    currentUser: User?,
    partner: User?,
    statusMe: ConnectionStatus,
    statusPartner: ConnectionStatus
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.offset(y = 4.dp),
                horizontalArrangement = Arrangement.spacedBy((-4).dp)
            ) {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp).offset(y = (-4).dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(2.dp, Color(0xFFFF4081).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape)
                    ) {
                        UserAvatar(
                            user = currentUser,
                            modifier = Modifier.fillMaxSize(),
                            showInitials = currentUser?.avatarUrl == null
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape)
                    ) {
                        UserAvatar(
                            user = partner,
                            modifier = Modifier.fillMaxSize(),
                            showInitials = partner?.avatarUrl == null
                        )
                    }
                }
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.offset(y = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(10.dp), tint = Color(0xFFFF4081))
                    Text(
                        text = "Together",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PartnerStatusCard(
    modifier: Modifier = Modifier,
    user: User,
    status: ConnectionStatus,
    currentTime: Long,
    distanceInfo: DistanceInfo? = null,
    isPartnerComingToMe: Boolean = false,
    isDistanceLoading: Boolean = false,
    distanceError: String? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = surfaceColor.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val speed = user.speed ?: 0f
                    if (speed > 0.3f) {
                        val speedKmh = (speed * 3.6f).toInt()
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$speedKmh km/h",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (status == ConnectionStatus.OFFLINE) {
                    Text(
                        text = "Last updated ${formatLastUpdated(user.lastLocationUpdate, currentTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (user.speed ?: 0f > 0.3f) {
                    if (distanceInfo != null) {
                        val distanceStatus = if (isPartnerComingToMe) {
                            "Coming to you • ${distanceInfo.distanceText} away"
                        } else {
                            "${distanceInfo.distanceText} away"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = distanceStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isDistanceLoading) {
                        Text(
                            text = "Calculating distance...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val level = user.batteryLevel
                        val isCharging = user.isCharging == true
                        val batteryRes = when {
                            isCharging -> when {
                                level == null -> Res.drawable.ic_battery_status
                                level <= 0.20f -> Res.drawable.ic_battery_charging_20
                                level <= 0.50f -> Res.drawable.ic_battery_charging_50
                                level <= 0.80f -> Res.drawable.ic_battery_charging_80
                                else -> Res.drawable.ic_battery_charging
                            }
                            else -> when {
                                level == null -> Res.drawable.ic_battery_status
                                level <= 0.20f -> Res.drawable.ic_battery_20
                                level <= 0.50f -> Res.drawable.ic_battery_50
                                level <= 0.80f -> Res.drawable.ic_battery_80
                                else -> Res.drawable.ic_battery_full
                            }
                        }
                        val batteryColor = when {
                            level == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            level <= 0.20f -> Color.Red
                            level <= 0.50f -> Color(0xFFFFA500)
                            else -> Color(0xFF4CAF50)
                        }

                        Icon(
                            painter = painterResource(batteryRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = (level?.let { "${(it * 100).toInt()}%" } ?: "--%"),
                            style = MaterialTheme.typography.labelSmall,
                            color = batteryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val netRes = when (user.connectionType) {
                            "WIFI" -> Res.drawable.ic_network_wifi
                            "CELLULAR" -> Res.drawable.ic_network_cellular
                            else -> Res.drawable.ic_network_cellular_off
                        }
                        Icon(painterResource(netRes), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = when (user.connectionType) {
                                "CELLULAR" -> "Cell"
                                null -> "Off"
                                else -> user.connectionType ?: "Off"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(8.dp)
                .offset(y = (-1).dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
                drawPath(path = path, color = surfaceColor.copy(alpha = 0.95f))
                val strokePath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2, size.height)
                    lineTo(size.width, 0f)
                }
                drawPath(path = strokePath, color = outlineColor, style = Stroke(width = 1.dp.toPx()))
            }
        }
    }
}



@Composable
fun OffScreenMarker(edgePoint: Offset, angle: Float, user: User?, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .offset { IntOffset(edgePoint.x.toInt() - 24.dp.toPx().toInt(), edgePoint.y.toInt() - 24.dp.toPx().toInt()) }
            .size(48.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(angle) {
                val path = Path().apply {
                    moveTo(size.width, size.height / 2)
                    lineTo(size.width - 15.dp.toPx(), size.height / 2 - 10.dp.toPx())
                    lineTo(size.width - 15.dp.toPx(), size.height / 2 + 10.dp.toPx())
                    close()
                }
                drawPath(path, Color(0xFFFF4081))
            }
        }
        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, border = BorderStroke(2.dp, Color(0xFFFF4081)), shadowElevation = 4.dp) {
            UserAvatar(user = user, modifier = Modifier.fillMaxSize(), showInitials = user?.avatarUrl == null)
        }
    }
}

@Composable
fun PartnerDashboard(
    modifier: Modifier = Modifier,
    anniversaryDate: Long?,
    currentTime: Long,
    onEditAnniversary: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onEditAnniversary() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (anniversaryDate != null) {
                val days = (currentTime - anniversaryDate) / (1000 * 60 * 60 * 24)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Together for $days days",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherWidget(
    modifier: Modifier = Modifier,
    weatherInfo: WeatherInfo?,
    isLoading: Boolean
) {
    AnimatedVisibility(
        visible = weatherInfo != null || isLoading,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoading && weatherInfo == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (weatherInfo != null) {
                    Text(
                        text = weatherInfo.iconCode,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${weatherInfo.temperature.toInt()}°C",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
