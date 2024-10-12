package com.hacoding.firebasegoogleauth

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hacoding.firebasegoogleauth.ui.theme.FirebaseGoogleAuthTheme
import com.hacoding.firebasegoogleauth.presentation.sign_in.GoogleAuthUiClient
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.hacoding.firebasegoogleauth.presentation.function.FunctionScreen
import com.hacoding.firebasegoogleauth.presentation.profile.ProfileScreen
import com.hacoding.firebasegoogleauth.presentation.sign_in.SignInScreen
import com.hacoding.firebasegoogleauth.presentation.sign_in.SignInState
import com.hacoding.firebasegoogleauth.presentation.sign_in.SignInViewModel
import com.hacoding.firebasegoogleauth.presentation.sign_in.UserData
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    private lateinit var functions: FirebaseFunctions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FirebaseGoogleAuthTheme {
                FirebaseApp.initializeApp(this)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "sign_in") {

                        //화면1 (로그인 화면)
                        composable("sign_in") {
                            val viewModel = viewModel<SignInViewModel>()
                            val state by viewModel.state.collectAsStateWithLifecycle()

                            LaunchedEffect(key1 = Unit) {
                                if(googleAuthUiClient.getSignedInUser() != null) {
                                    navController.navigate("profile")
                                }
                            }

                            val launcher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartIntentSenderForResult(),
                                onResult = { result ->
                                    if(result.resultCode == RESULT_OK) {
                                        lifecycleScope.launch {
                                            val signInResult = googleAuthUiClient.signInWithIntent(
                                                intent = result.data ?: return@launch
                                            )
                                            viewModel.onSignInResult(signInResult)
                                        }
                                    }
                                }
                            )

                            LaunchedEffect(key1 = state.isSignInSuccessful) {
                                if(state.isSignInSuccessful) {
                                    Toast.makeText(
                                        applicationContext,
                                        "Sign in successful",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    navController.navigate("profile")
                                    viewModel.resetState()
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SignInScreen(
                                    state = state,
                                    onSignInClick = {
                                        lifecycleScope.launch {
                                            val signInIntentSender = googleAuthUiClient.signIn()
                                            launcher.launch(
                                                IntentSenderRequest.Builder(
                                                    signInIntentSender ?: return@launch
                                                ).build()
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                //helloWorld firebase function
                                FunctionScreen(
                                    title = "helloWorld",
                                    functionClick = {
                                        functions = FirebaseFunctions.getInstance("asia-northeast3") // 지역을 명시해 주세요
                                        functions
                                            .getHttpsCallable("helloWorld")
                                            .call()
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val result = task.result?.data as? Map<*, *>
                                                    Log.d("hasung", result?.get("message") as? String ?: "No data")
                                                    Toast.makeText(applicationContext, result?.get("message") as? String ?: "No data", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Log.e("hasung", "Function call failed", task.exception)
                                                    Toast.makeText(applicationContext, "Function call failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    }
                                )

                                //유저 리스트 가져오는 firebase function
                                FunctionScreen(
                                    title = "getUserList",
                                    functionClick = {
                                        val functions = FirebaseFunctions.getInstance("asia-northeast3") // 지역 설정
                                        functions
                                            .getHttpsCallable("getUserList")
                                            .call()
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val result = task.result?.data as? Map<*, *>  // 응답 데이터 Map 형태로 캐스팅
                                                    val success = result?.get("success") as? Boolean ?: false
                                                    val message = result?.get("message") as? String ?: "No data"

                                                    if (success) {
                                                        val users = result?.get("users") as? List<Map<String, Any>>
                                                        if (users != null) {
                                                            // 사용자 목록을 처리
                                                            for (user in users) {
                                                                val uid = user["uid"] as? String
                                                                val email = user["email"] as? String
                                                                Log.d("hasung", "User ID: $uid, Email: $email")
                                                            }
                                                        }
                                                    }

                                                    // 성공 메시지 표시
                                                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Log.e("hasung", "Function call failed", task.exception)
                                                    Toast.makeText(applicationContext, "Function call failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    }
                                )
                            }
                        }

                        //화면2 (프로필 화면)
                        composable("profile") {
                            ProfileScreen(
                                userData = googleAuthUiClient.getSignedInUser(),
                                onSignOut = {
                                    lifecycleScope.launch {
                                        googleAuthUiClient.signOut()
                                        Toast.makeText(
                                            applicationContext,
                                            "Signed out",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    FirebaseGoogleAuthTheme {
        SignInScreen(
            state = SignInState(true),
            onSignInClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    FirebaseGoogleAuthTheme {
        ProfileScreen(
            userData = UserData("testId", "testName", null),
            onSignOut = {}
        )
    }
}