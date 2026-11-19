package com.opentube.tv.ui.signin

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.opentube.tv.data.YouTube
import com.opentube.tv.ui.components.LoadingLine
import com.opentube.tv.ui.components.PillButton
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

/** 电视码登录：手机扫码或打开 yt.be/activate 输入代码 */
@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    DisposableEffect(attempt) {
        code = null; error = null
        val d = YouTube.signIn.signInObserve().subscribe(
            { code = it },
            { error = it.message ?: "获取登录码失败" },
            { onDone() },
        )
        onDispose { d.dispose() }
    }
    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().background(Film.Base), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(380.dp)) {
                Text("登录 YouTube", style = Type.HeroTitle)
                Spacer(Modifier.height(12.dp))
                Text("用手机扫右边的二维码，或在任意浏览器打开下面的地址，输入代码。", style = Type.Body1.copy(color = Film.Edge2))
                Spacer(Modifier.height(28.dp))
                Text("yt.be/activate", style = Type.Facts.copy(color = Film.Edge, fontSize = Type.ScreenTitle.fontSize))
                Spacer(Modifier.height(10.dp))
                when {
                    error != null -> {
                        Text(error!!, style = Type.Body1.copy(color = Film.Grease))
                        Spacer(Modifier.height(16.dp))
                        PillButton("重新获取代码", onClick = { attempt++ })
                    }
                    code == null -> LoadingLine(Modifier.width(200.dp).padding(vertical = 26.dp))
                    else -> {
                        Text(code!!, style = Type.BigCode)
                        Spacer(Modifier.height(12.dp))
                        Text("在手机上确认后，这里会自动进入。", style = Type.Label)
                    }
                }
            }
            Box(
                Modifier.size(220.dp).background(Film.Edge, RoundedCornerShape(6.dp)).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                val c = code
                if (c != null) {
                    val bmp = remember(c) { qr("https://youtube.com/qr/activate/" + c.replace(" ", "-"), 512) }
                    Image(bmp.asImageBitmap(), contentDescription = "登录二维码", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun qr(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    val ink = 0xFF0F0D0B.toInt(); val paper = 0xFFE9E0CC.toInt()
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) ink else paper }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
