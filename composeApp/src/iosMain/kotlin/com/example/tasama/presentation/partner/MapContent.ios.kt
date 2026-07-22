package com.example.tasama.presentation.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.interop.UIKitView
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
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.RoutePoint
import com.example.tasama.domain.model.Story
import com.example.tasama.domain.model.User
import com.example.tasama.domain.model.WeatherInfo
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.domain.repository.TravelMode
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.*

import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas

val MapHeaderHeight = 88.dp
val MapFabsHeight = 192.dp
val MapFabsWidth = 56.dp

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

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        return when (rendererForOverlay) {
            is MKPolyline -> {
                val title = rendererForOverlay.title
                MKPolylineRenderer(rendererForOverlay).apply {
                    when (title) {
                        "Journey" -> {
                            strokeColor = UIColor.orangeColor
                            lineWidth = 4.0
                        }
                        "Story" -> {
                            strokeColor = UIColor.colorWithRed(1.0, 0.25, 0.5, 1.0) // 0xFFFF4081
                            lineWidth = 4.0
                        }
                        "Route" -> {
                            strokeColor = UIColor.blueColor
                            lineWidth = 4.0
                        }
                        "Dashed" -> {
                            strokeColor = UIColor.blueColor
                            lineWidth = 3.0
                            lineDashPattern = listOf(10, 10)
                        }
                        else -> {
                            strokeColor = UIColor.blueColor
                            lineWidth = 3.0
                        }
                    }
                }
            }
            else -> MKOverlayRenderer(rendererForOverlay)
        }
    }

    override fun mapViewDidChangeVisibleRegion(mapView: MKMapView) {
        onRegionChanged(mapView)
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    stories: List<Story>,
    anniversaryDate: Long?,
    etaInfo: EtaInfo?,
    weatherInfo: WeatherInfo?,
    isWeatherLoading: Boolean,
    travelMode: TravelMode,
    isPartnerComingToMe: Boolean,
    isEtaLoading: Boolean,
    etaError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onDeleteStory: (Story) -> Unit,
    onUpdateStory: (Story) -> Unit,
    onSetTravelMode: (TravelMode) -> Unit,
    onUnlink: () -> Unit,
    selectedStoryForMap: Story?,
    onClearSelectedStory: () -> Unit,
    onSaveJourney: (String, String, String, List<ByteArray>) -> Unit,
    currentDayRoute: List<RoutePoint>,
    isRouteLoading: Boolean,
    fetchTodayRoute: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPreviewingJourney by rememberSaveable { mutableStateOf(false) }
    var showSaveJourneySheet by remember { mutableStateOf(false) }
    var showAddStorySheet by remember { mutableStateOf<Location?>(null) }
    var editingStory by remember { mutableStateOf<Story?>(null) }
    var showAddPlaceSheet by remember { mutableStateOf<Location?>(null) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    var showDeletePlaceDialog by remember { mutableStateOf<Place?>(null) }
    var showDeleteStoryDialog by remember { mutableStateOf<Story?>(null) }
    var isRouteEnabled by rememberSaveable { mutableStateOf(false) }
    
    // MKMapView doesn't easily expose click coordinates in a way that's trivial to bridge here without a Coordinator/Delegate.
    // For now, we'll focus on the UI components and the Map placeholder.
    // In a real implementation, we'd use a custom MKMapView delegate.

    var currentTime by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTime = Clock.System.now().toEpochMilliseconds()
        }
    }

    var currentDayPolyline by remember { mutableStateOf<MKPolyline?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var mapViewInstance by remember { mutableStateOf<MKMapView?>(null) }
    var regionChangeTicket by remember { mutableIntStateOf(0) }

    val myLocation = remember(currentUser?.latitude, currentUser?.longitude) {
        if (currentUser?.latitude != null && currentUser.longitude != null) {
            Location(currentUser.latitude, currentUser.longitude)
        } else null
    }

    val partnerLocation = remember(partner?.latitude, partner?.longitude) {
        if (partner?.latitude != null && partner.longitude != null) {
            Location(partner.latitude, partner.longitude)
        } else null
    }

    val distance by remember(myLocation, partnerLocation, etaInfo) {
        derivedStateOf {
            if (myLocation != null && partnerLocation != null &&
                myLocation.latitude != 0.0 && partnerLocation.latitude != 0.0) {
                calculateDistance(myLocation, partnerLocation)
            } else null
        }
    }

    val isTogether by remember(distance) {
        derivedStateOf { (distance ?: Double.MAX_VALUE) < 25.0 }
    }

    val markerData by remember(myLocation, partnerLocation, mapSize, density, regionChangeTicket, isTogether) {
        derivedStateOf {
            val map = mapViewInstance ?: return@derivedStateOf null
            if (mapSize == IntSize.Zero) return@derivedStateOf null

            val width = mapSize.width.toFloat()
            val height = mapSize.height.toFloat()
            val center = Offset(width / 2, height / 2)

            fun getScreenPos(loc: Location?): Offset? {
                if (loc == null) return null
                val coord = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                val pt = map.convertCoordinate(coord, toPointToView = map)
                return pt.useContents { Offset(x.toFloat() * density.density, y.toFloat() * density.density) }
            }

            val myScreenPos = getScreenPos(myLocation)
            val partnerScreenPos = getScreenPos(partnerLocation)

            val isMeVisible = myScreenPos?.let { it.x in 0f..width && it.y in 0f..height } ?: false
            val isPartnerVisible = partnerScreenPos?.let { it.x in 0f..width && it.y in 0f..height } ?: false

            var myEdgePoint: Offset? = null
            var myAngle = 0f
            if (myScreenPos != null && !isMeVisible) {
                val intersection = findRayIntersection(center, myScreenPos, width, height)
                myEdgePoint = applyUIAvoidance(
                    intersection, width, height,
                    avoidanceMarginPx = with(density) { 16.dp.toPx() },
                    indicatorRadiusPx = with(density) { 28.dp.toPx() },
                    headerHeightPx = with(density) { MapHeaderHeight.toPx() },
                    fabsWidthPx = with(density) { MapFabsWidth.toPx() },
                    fabsHeightPx = with(density) { MapFabsHeight.toPx() }
                )
                myAngle = atan2(myScreenPos.y - center.y, myScreenPos.x - center.x) * 180 / PI.toFloat()
            }

            var partnerEdgePoint: Offset? = null
            var partnerAngle = 0f
            if (partnerScreenPos != null && !isPartnerVisible) {
                val intersection = findRayIntersection(center, partnerScreenPos, width, height)
                partnerEdgePoint = applyUIAvoidance(
                    intersection, width, height,
                    avoidanceMarginPx = with(density) { 16.dp.toPx() },
                    indicatorRadiusPx = with(density) { 28.dp.toPx() },
                    headerHeightPx = with(density) { MapHeaderHeight.toPx() },
                    fabsWidthPx = with(density) { MapFabsWidth.toPx() },
                    fabsHeightPx = with(density) { MapFabsHeight.toPx() }
                )
                partnerAngle = atan2(partnerScreenPos.y - center.y, partnerScreenPos.x - center.x) * 180 / PI.toFloat()
            }

            MapMarkerVisibilityData(
                isMeVisible = isMeVisible,
                isPartnerVisible = isPartnerVisible,
                myEffectiveLocation = myLocation ?: Location(0.0, 0.0),
                partnerEffectiveLocation = partnerLocation ?: Location(0.0, 0.0),
                myEdgePoint = myEdgePoint,
                partnerEdgePoint = partnerEdgePoint,
                myAngle = myAngle,
                partnerAngle = partnerAngle,
                showPolyline = isRouteEnabled && isPartnerComingToMe,
                partnerScreenPos = partnerScreenPos
            )
        }
    }

    fun fitAllMarkers() {
        val map = mapViewInstance ?: return
        val locations = mutableListOf<Location>()
        myLocation?.let { locations.add(it) }
        partnerLocation?.let { locations.add(it) }
        places.forEach { locations.add(Location(it.latitude, it.longitude)) }
        
        if (locations.isEmpty()) return

        memScoped {
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            
            locations.forEach { loc ->
                val point = MKMapPointForCoordinate(CLLocationCoordinate2DMake(loc.latitude, loc.longitude))
                point.useContents {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
            
            val rect = MKMapRectMake(minX, minY, maxX - minX, maxY - minY)
            map.setVisibleMapRect(
                rect, 
                edgePadding = UIEdgeInsetsMake(100.0, 100.0, 100.0, 100.0), 
                animated = true
            )
        }
    }

    val coordinator = remember {
        MapCoordinator(
            onMapClick = { lat, lng ->
                // Handle map click
                val clickedStory = stories.find { story ->
                    calculateDistance(Location(lat, lng), Location(story.latitude, story.longitude)) <= 50.0
                }
                if (clickedStory != null) {
                    editingStory = clickedStory
                    showAddStorySheet = Location(clickedStory.latitude, clickedStory.longitude)
                } else {
                    val clickedPlace = places.find { place ->
                        calculateDistance(Location(lat, lng), Location(place.latitude, place.longitude)) <= maxOf(place.radius, 50.0)
                    }
                    if (clickedPlace != null) {
                        editingPlace = clickedPlace
                        showAddPlaceSheet = Location(clickedPlace.latitude, clickedPlace.longitude)
                    }
                }
            },
            onRegionChanged = {
                regionChangeTicket++
            }
        )
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { mapSize = it }) {
        UIKitView(
            factory = {
                MKMapView().apply {
                    delegate = coordinator
                    val tapGesture = platform.UIKit.UITapGestureRecognizer(target = coordinator, action = NSSelectorFromString("handleTap:"))
                    addGestureRecognizer(tapGesture)
                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                // Update polylines
                mapView.overlays.forEach { if (it is MKPolyline) mapView.removeOverlay(it) }
                
                if (isPreviewingJourney && currentDayRoute.isNotEmpty()) {
                    val coords = currentDayRoute.map { CLLocationCoordinate2DMake(it.latitude, it.longitude) }
                    memScoped {
                        val nativeCoords = allocArray<CLLocationCoordinate2D>(coords.size)
                        coords.forEachIndexed { index, coord ->
                            nativeCoords[index].latitude = coord.useContents { latitude }
                            nativeCoords[index].longitude = coord.useContents { longitude }
                        }
                        val polyline = MKPolyline.polylineWithCoordinates(nativeCoords, coords.size.toULong())
                        polyline.setTitle("Journey")
                        mapView.addOverlay(polyline)
                    }
                }

                selectedStoryForMap?.let { story ->
                    if (story.route.isNotEmpty()) {
                        val coords = story.route.map { CLLocationCoordinate2DMake(it.latitude, it.longitude) }
                        memScoped {
                            val nativeCoords = allocArray<CLLocationCoordinate2D>(coords.size)
                            coords.forEachIndexed { index, coord ->
                                nativeCoords[index].latitude = coord.useContents { latitude }
                                nativeCoords[index].longitude = coord.useContents { longitude }
                            }
                            val polyline = MKPolyline.polylineWithCoordinates(nativeCoords, coords.size.toULong())
                            polyline.setTitle("Story")
                            mapView.addOverlay(polyline)
                        }
                    }
                }

                markerData?.let { data ->
                    if (data.showPolyline) {
                        // For now, we'll draw a straight dashed line if we don't have the full route yet
                        // or just as a fallback
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

                // Update annotations
                mapView.removeAnnotations(mapView.annotations)
                val annotations = stories.map { story ->
                    MKPointAnnotation().apply {
                        setCoordinate(CLLocationCoordinate2DMake(story.latitude, story.longitude))
                        setTitle(story.title)
                    }
                } + places.map { place ->
                    MKPointAnnotation().apply {
                        setCoordinate(CLLocationCoordinate2DMake(place.latitude, place.longitude))
                        setTitle(place.name)
                    }
                }
                mapView.addAnnotations(annotations)
            }
        )

        // Markers and Indicators
        markerData?.let { data ->
            // Combined Together Marker
            if (isTogether && myLocation != null && partnerLocation != null) {
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

            // My Marker
            if (data.isMeVisible && !isTogether) {
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
            } else if (data.myEdgePoint != null && !isTogether) {
                OffScreenMarker(edgePoint = data.myEdgePoint, angle = data.myAngle, user = currentUser, onTap = { fitAllMarkers() })
            }

            // Partner Marker
            if (data.isPartnerVisible && !isTogether) {
                data.partnerScreenPos?.let {
                    Box(modifier = Modifier.offset { IntOffset(it.x.toInt() - 32.dp.toPx().toInt(), it.y.toInt() - 48.dp.toPx().toInt()) }) {
                        UserMarker(partner, false, rememberPartnerStatus(partner, currentTime), onClick = { isPartnerInfoVisible = !isPartnerInfoVisible })
                    }
                }
            } else if (data.partnerEdgePoint != null && !isTogether) {
                OffScreenMarker(edgePoint = data.partnerEdgePoint, angle = data.partnerAngle, user = partner, onTap = { fitAllMarkers() })
            }
        }

        // Partner Status Card
        if (partner != null && isPartnerInfoVisible) {
            val status = rememberPartnerStatus(partner, currentTime)
            PartnerStatusCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .padding(horizontal = 16.dp),
                user = partner,
                status = status,
                currentTime = currentTime,
                etaInfo = etaInfo,
                isPartnerComingToMe = isPartnerComingToMe,
                isEtaLoading = isEtaLoading,
                etaError = etaError
            )
        }

        // UI Overlays (Shared logic ported from Android)
        
        PartnerDashboard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            anniversaryDate = anniversaryDate,
            currentTime = currentTime,
            onEditAnniversary = onEditAnniversary
        )

        WeatherWidget(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 14.dp, end = 16.dp),
            weatherInfo = weatherInfo,
            isLoading = isWeatherLoading
        )

        // Journey Save Overlay
        AnimatedVisibility(
            visible = isPreviewingJourney && currentDayRoute.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Button(
                onClick = { showSaveJourneySheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                elevation = ButtonDefaults.buttonElevation(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Journey as Story")
            }
        }

        // Fabs
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (!isPreviewingJourney) {
                        fetchTodayRoute()
                        isPreviewingJourney = true
                    } else {
                        isPreviewingJourney = false
                        onClearSelectedStory()
                    }
                },
                containerColor = if (isPreviewingJourney) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = if (isPreviewingJourney) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.tertiary,
                shape = CircleShape
            ) {
                if (isRouteLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = if (isPreviewingJourney) Icons.Default.Close else Icons.Default.History, contentDescription = null)
                }
            }

            // Recenter/Fit Button
            SmallFloatingActionButton(
                onClick = { fitAllMarkers() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit Markers")
            }

            // Route Toggle Button
            SmallFloatingActionButton(
                onClick = { isRouteEnabled = !isRouteEnabled },
                containerColor = if (isRouteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = if (isRouteEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Directions, contentDescription = "Toggle Route")
            }

            // Travel Mode Selector
            AnimatedVisibility(
                visible = isRouteEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TravelModeButton(
                            selected = travelMode == TravelMode.DRIVING,
                            onClick = { onSetTravelMode(TravelMode.DRIVING) },
                            icon = Icons.Default.DirectionsCar
                        )
                        TravelModeButton(
                            selected = travelMode == TravelMode.MOTORCYCLE,
                            onClick = { onSetTravelMode(TravelMode.MOTORCYCLE) },
                            icon = Icons.Default.TwoWheeler
                        )
                        TravelModeButton(
                            selected = travelMode == TravelMode.WALKING,
                            onClick = { onSetTravelMode(TravelMode.WALKING) },
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk
                        )
                    }
                }
            }
        }

        // Sheets
        if (showSaveJourneySheet) {
            ModalBottomSheet(
                onDismissRequest = { showSaveJourneySheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                SaveJourneySheetContent(
                    route = currentDayRoute,
                    onSave = { title, desc, cat, photos ->
                        onSaveJourney(title, desc, cat, photos)
                        showSaveJourneySheet = false
                        isPreviewingJourney = false
                    },
                    onCancel = { showSaveJourneySheet = false }
                )
            }
        }

        if (showAddPlaceSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddPlaceSheet = null
                    editingPlace = null
                }
            ) {
                AddPlaceSheetContent(
                    location = showAddPlaceSheet!!,
                    initialPlace = editingPlace,
                    onAddPlace = { place ->
                        onAddPlace(place)
                        showAddPlaceSheet = null
                        editingPlace = null
                    },
                    onCancel = { 
                        showAddPlaceSheet = null
                        editingPlace = null
                    }
                )
            }
        }

        if (showAddStorySheet != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddStorySheet = null
                    editingStory = null
                }
            ) {
                AddStorySheetContent(
                    location = showAddStorySheet!!,
                    initialStory = editingStory,
                    onAddStory = { story, bytes ->
                        onAddStory(story, bytes)
                        showAddStorySheet = null
                        editingStory = null
                    },
                    onUpdateStory = { story ->
                        onUpdateStory(story)
                        showAddStorySheet = null
                        editingStory = null
                    },
                    onCancel = { 
                        showAddStorySheet = null
                        editingStory = null
                    },
                    onCreatePlace = { loc, title, addr ->
                        showAddStorySheet = null
                        editingStory = null
                        editingPlace = Place(name = title, address = addr, latitude = loc.latitude, longitude = loc.longitude)
                        showAddPlaceSheet = loc
                    }
                )
            }
        }

        if (showDeletePlaceDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeletePlaceDialog = null },
                title = { Text("Delete Place") },
                text = { Text("Are you sure you want to delete \"${showDeletePlaceDialog?.name}\"? You will no longer receive notifications for this location.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeletePlaceDialog?.let { onDeletePlace(it.id) }
                            showDeletePlaceDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePlaceDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteStoryDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteStoryDialog = null },
                title = { Text("Delete Story") },
                text = { Text("Are you sure you want to delete \"${showDeleteStoryDialog?.title}\"? This memory will be removed for both you and your partner.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteStoryDialog?.let { onDeleteStory(it) }
                            showDeleteStoryDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteStoryDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AddPlaceSheetContent(
    location: Location,
    initialPlace: Place? = null,
    onAddPlace: (Place) -> Unit,
    onCancel: () -> Unit
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
        delay(500)
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
        Text(
            text = if (initialPlace == null) "Add Place Marker" else "Edit Place Marker",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NotificationChip(label = "Entry", selected = notifyOnEntry, onClick = { notifyOnEntry = !notifyOnEntry }, modifier = Modifier.weight(1f))
            NotificationChip(label = "Exit", selected = notifyOnExit, onClick = { notifyOnExit = !notifyOnExit }, modifier = Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }

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
                        color = (selectedColor.value shr 32).toLong(),
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
fun NotificationChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun TravelModeButton(selected: Boolean, onClick: () -> Unit, icon: ImageVector) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConnectionStatusBadge(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        ConnectionStatus.LIVE -> "Live" to Color(0xFF4CAF50)
        ConnectionStatus.WEAK -> "Weak GPS" to Color(0xFFFF9800)
        ConnectionStatus.OFFLINE -> "No Signal" to Color(0xFFF44336)
    }
    Surface(color = color, shape = RoundedCornerShape(percent = 50), modifier = modifier) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

enum class ConnectionStatus { LIVE, WEAK, OFFLINE }

@Composable
fun rememberPartnerStatus(user: User?, now: Long): ConnectionStatus {
    if (user == null) return ConnectionStatus.OFFLINE
    return remember(user.lastLocationUpdate, user.accuracy, now) {
        val lastUpdate = user.lastLocationUpdate ?: 0L
        val delayMs = now - lastUpdate
        val accuracy = user.accuracy ?: 0f
        when {
            lastUpdate == 0L || delayMs > 30_000 -> ConnectionStatus.OFFLINE
            delayMs > 10_000 || accuracy > 50f -> ConnectionStatus.WEAK
            else -> ConnectionStatus.LIVE
        }
    }
}

fun formatLastUpdated(lastUpdate: Long?, now: Long): String {
    if (lastUpdate == null) return "never"
    val diffSec = (now - lastUpdate) / 1000
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
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
        // Background Glow/Circle
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
            // Heart icons above
            Row(
                modifier = Modifier.offset(y = 4.dp),
                horizontalArrangement = Arrangement.spacedBy((-4).dp)
            ) {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp).offset(y = (-4).dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
            }

            // Avatars Bubble
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

            // Small triangle pointing down
            val surfaceColor = MaterialTheme.colorScheme.surface
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
                    drawPath(
                        path = path,
                        color = surfaceColor
                    )
                }
            }
            
            // Location Name
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
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
fun UserMarker(user: User?, isMe: Boolean, status: ConnectionStatus, onClick: () -> Unit = {}) {
    val speed = user?.speed ?: 0f
    val isMoving = speed > 0.3f && status != ConnectionStatus.OFFLINE
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        if (isMoving) {
            Box(modifier = Modifier.size(64.dp).background(if (isMe) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape))
        }
        Box(
            modifier = Modifier
                .offset(y = 8.dp)
                .size(48.dp)
                .background(if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, CircleShape)
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(user = user, modifier = Modifier.fillMaxSize(), showInitials = user?.avatarUrl == null)
        }
    }
}

@Composable
fun StoryMarker(story: Story) {
    Box(
        modifier = Modifier.size(48.dp).background(Color.White, CircleShape).border(2.dp, Color(0xFFFF4081), CircleShape).padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (story.photoUrls.isNotEmpty()) {
            AsyncImage(model = story.photoUrls.first(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4081), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun PartnerStatusCard(
    modifier: Modifier = Modifier,
    user: User,
    status: ConnectionStatus,
    currentTime: Long,
    etaInfo: EtaInfo? = null,
    isPartnerComingToMe: Boolean = false,
    isEtaLoading: Boolean = false,
    etaError: String? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = user.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            if (status == ConnectionStatus.OFFLINE) {
                Text(text = "Last updated ${formatLastUpdated(user.lastLocationUpdate, currentTime)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Battery
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
                            else -> Res.drawable.ic_battery_100
                        }
                    }
                    val batteryColor = when {
                        level == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        level <= 0.20f -> Color.Red
                        level <= 0.50f -> Color(0xFFFFA500)
                        else -> Color(0xFF4CAF50)
                    }

                    Icon(painterResource(batteryRes), null, modifier = Modifier.size(16.dp), tint = Color.Unspecified)
                    Text(text = (level?.let { "${(it * 100).toInt()}%" } ?: "--%"), style = MaterialTheme.typography.labelSmall, color = batteryColor, fontWeight = FontWeight.Bold)
                }

                // Signal
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(Res.drawable.ic_signal_status), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = when (user.connectionType) {
                            "Cellular" -> "Cell"
                            null -> "Off"
                            else -> user.connectionType
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun OffScreenMarker(edgePoint: Offset, angle: Float, user: User?, onTap: () -> Unit) {
    val indicatorSize = 56.dp
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset((edgePoint.x - with(density) { 28.dp.toPx() }).toInt(), (edgePoint.y - with(density) { 28.dp.toPx() }).toInt()) }
            .size(indicatorSize)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            val arrowPath = Path().apply {
                moveTo(radius + radius * 0.7f, radius)
                lineTo(radius + radius * 0.9f, radius - 6.dp.toPx())
                lineTo(radius + radius * 0.9f, radius + 6.dp.toPx())
                close()
            }
            rotate(angle) {
                drawPath(arrowPath, color = Color.White)
            }
        }
        Box(modifier = Modifier.size(indicatorSize - 16.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp), contentAlignment = Alignment.Center) {
            UserAvatar(user = user, modifier = Modifier.fillMaxSize(), showInitials = user?.avatarUrl == null)
        }
    }
}

// Reuse logic from Android implementation, replacing LatLng with Location

@Composable
fun SaveJourneySheetContent(
    route: List<RoutePoint>,
    onSave: (String, String, String, List<ByteArray>) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("Today's Journey") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Journey") }
    val newPhotos = remember { mutableStateListOf<Pair<String, ByteArray>>() }
    val photoUrls = remember { mutableStateListOf<String>() }

    val pickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        onResult = { file: PlatformFile? ->
            file?.let { platformFile ->
                scope.launch {
                    val bytes = platformFile.readBytes()
                    val previewUrl = platformFile.path ?: "temp_${Clock.System.now().toEpochMilliseconds()}"
                    newPhotos.add(previewUrl to bytes)
                    photoUrls.add(previewUrl)
                }
            }
        }
    )

    val distance = remember(route) {
        var total = 0.0
        for (i in 0 until route.size - 1) {
            total += calculateDistance(
                Location(route[i].latitude, route[i].longitude),
                Location(route[i + 1].latitude, route[i + 1].longitude)
            )
        }
        total
    }

    val durationMs = remember(route) {
        if (route.size > 1) route.last().timestamp - route.first().timestamp else 0L
    }

    val durationText = remember(durationMs) {
        val hours = durationMs / 3600000
        val minutes = (durationMs % 3600000) / 60000
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Save Today's Journey",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Distance", style = MaterialTheme.typography.labelMedium)
                    val distText = if (distance < 1000) "${distance.toInt()}m" else "${(distance / 1000).format(1)}km"
                    Text(distText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Duration", style = MaterialTheme.typography.labelMedium)
                    Text(durationText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedButton(
            onClick = { pickerLauncher.launch() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AddAPhoto, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Photo")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(title, description, category, newPhotos.map { it.second }) },
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Save Journey")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStorySheetContent(
    location: Location,
    initialStory: Story? = null,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onUpdateStory: (Story) -> Unit,
    onCancel: () -> Unit,
    onCreatePlace: (Location, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialStory?.title ?: "") }
    var description by remember { mutableStateOf(initialStory?.description ?: "") }
    var dateMillis by remember { mutableLongStateOf(initialStory?.date ?: Clock.System.now().toEpochMilliseconds()) }
    var category by remember { mutableStateOf(initialStory?.category ?: "Memory") }
    var address by remember { mutableStateOf(initialStory?.address ?: "Fetching address...") }
    val photoUrls = remember { mutableStateListOf<String>().apply { initialStory?.photoUrls?.let { addAll(it) } } }
    val newPhotos = remember { mutableStateListOf<Pair<String, ByteArray>>() }
    
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(location, initialStory) {
        if (initialStory != null) return@LaunchedEffect
        val result = reverseGeocode(location.latitude, location.longitude)
        if (result != null) {
            address = result
        }
    }

    val pickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        onResult = { file: PlatformFile? ->
            file?.let { platformFile ->
                scope.launch {
                    val bytes = platformFile.readBytes()
                    val previewUrl = platformFile.path ?: "temp_${Clock.System.now().toEpochMilliseconds()}"
                    newPhotos.add(previewUrl to bytes)
                    photoUrls.add(previewUrl)
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (initialStory == null) "New Story" else "Edit Story",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            val dateStr = remember(dateMillis) {
                val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(dateMillis)
                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${dateTime.day} ${dateTime.month.name}, ${dateTime.year}"
            }
            Text(text = dateStr, style = MaterialTheme.typography.bodyLarge)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val story = Story(
                        id = initialStory?.id ?: "",
                        title = title,
                        description = description,
                        date = dateMillis,
                        category = category,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        photoUrls = photoUrls.filter { !it.startsWith("temp_") }
                    )
                    if (initialStory == null) {
                        onAddStory(story, newPhotos.map { it.second })
                    } else {
                        onUpdateStory(story)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Story")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Together for $days days", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoading && weatherInfo == null) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (weatherInfo != null) {
                    Text(text = weatherInfo.iconCode, style = MaterialTheme.typography.titleMedium)
                    Text(text = "${weatherInfo.temperature.toInt()}°C", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
