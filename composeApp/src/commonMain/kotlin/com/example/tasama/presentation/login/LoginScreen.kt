package com.example.tasama.presentation.login

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import tasama.composeapp.generated.resources.Res
import tasama.composeapp.generated.resources.logo

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel(),
    onGoogleSignInClick: () -> Unit = {},
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(uiState.isRegister) {
        pagerState.animateScrollToPage(if (uiState.isRegister) 1 else 0)
    }

    LaunchedEffect(pagerState.currentPage) {
        val shouldBeRegister = pagerState.currentPage == 1
        if (uiState.isRegister != shouldBeRegister) {
            viewModel.toggleMode()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "Tasama Logo",
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tasama",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Mode Selector (Segmented-like toggle)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.width(300.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val signInSelected = !uiState.isRegister
                    val signUpSelected = uiState.isRegister

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (signInSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { if (uiState.isRegister) viewModel.toggleMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sign In",
                            color = if (signInSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (signInSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (signUpSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { if (!uiState.isRegister) viewModel.toggleMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sign Up",
                            color = if (signUpSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (signUpSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (page == 1) { // Sign Up
                                OutlinedTextField(
                                    value = uiState.name,
                                    onValueChange = viewModel::onNameChange,
                                    label = { Text("Full Name") },
                                    placeholder = { Text("Enter your name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = viewModel::onEmailChange,
                                label = { Text("Email") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true
                            )

                            var passwordVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = uiState.password,
                                onValueChange = viewModel::onPasswordChange,
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true
                            )

                            if (uiState.error != null) {
                                Text(
                                    text = uiState.error!!,
                                    color = if (uiState.error!!.contains("sent")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            if (page == 0) { // Sign In
                                TextButton(
                                    onClick = viewModel::resetPassword,
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        "Forgot Password?",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { if (page == 1) viewModel.register() else viewModel.login() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !uiState.isLoading
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (page == 1) "Sign Up" else "Sign In",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (page == 0) { // Sign In
                                TextButton(
                                    onClick = viewModel::loginAnonymously,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Sign in as Guest")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (page == 0) { // Sign In
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "OR",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = onGoogleSignInClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("G", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Continue with Google")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
