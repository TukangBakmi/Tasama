package com.example.tasama.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.tasama.domain.model.User
import com.example.tasama.util.disableHardwareBitmaps
import org.jetbrains.compose.resources.painterResource
import tasama.composeapp.generated.resources.*

@Composable
fun UserAvatar(
    user: User?,
    modifier: Modifier = Modifier,
    fallbackName: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showInitials: Boolean = true
) {
    val avatarUrl = user?.avatarUrl
    val name = user?.name ?: fallbackName ?: "?"
    
    val avatarRes = remember(avatarUrl) {
        val resName = avatarUrl?.replace(".png", "")?.lowercase()
        when (resName) {
            "avatar_1", "avatar1" -> Res.drawable.Avatar1
            "avatar_2", "avatar2" -> Res.drawable.Avatar2
            "avatar_3", "avatar3" -> Res.drawable.Avatar3
            "avatar_4", "avatar4" -> Res.drawable.Avatar4
            "avatar_5", "avatar5" -> Res.drawable.Avatar5
            "avatar_6", "avatar6" -> Res.drawable.Avatar6
            "avatar_7", "avatar7" -> Res.drawable.Avatar7
            "avatar_8", "avatar8" -> Res.drawable.Avatar8
            "avatar_9", "avatar9" -> Res.drawable.Avatar9
            else -> null
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarRes != null) {
            Image(
                painter = painterResource(avatarRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!avatarUrl.isNullOrEmpty()) {
            val context = LocalPlatformContext.current
            val imageRequest = remember(avatarUrl, context) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .disableHardwareBitmaps()
                    .crossfade(false) // Better compatibility for Map snapshots
                    .build()
            }
            
            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                contentScale = ContentScale.Crop
            )
            val state by painter.state.collectAsState()

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (state !is AsyncImagePainter.State.Success && showInitials) {
                InitialsFallback(name, contentColor)
            }
        } else if (showInitials) {
            InitialsFallback(name, contentColor)
        }
    }
}

@Composable
private fun InitialsFallback(name: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}
