@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshizhuizong.ui.screens.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshizhuizong.data.TrailerLink
import com.example.yingshizhuizong.util.openYouTube
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * 预告片播放页：16:9 播放器 + 影片信息。
 *
 * - YouTube 预告片 → 内嵌播放器（需要能访问 YouTube 的网络）
 * - 手动填写的 mp4/m3u8 直链 → <video> 播放
 * - 其他网页链接 → 应用内打开
 *
 * 页面会明确区分「正在查找」「正在加载播放器」「播放器加载失败」三种状态，
 * 失败时给出浏览器 / B 站 / 手动直链三种兜底方式，不会一直转圈。
 */
@Composable
fun TrailerScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm: TrailerViewModel = viewModel()
    val item by vm.item.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ready = state as? TrailerState.Ready

    var playerProblem by remember { mutableStateOf<String?>(null) }
    var playerLoaded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    // 内嵌播放器报 Error 153 时，可以改用手机版 YouTube 网页播放
    var useMobilePage by remember { mutableStateOf(false) }

    // 换播放源或重试时重置播放器状态
    LaunchedEffect(ready?.source) {
        playerProblem = null
        playerLoaded = false
        useMobilePage = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(ready?.title ?: item?.title ?: "预告片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { playExternally(context, ready) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "在 YouTube 中打开")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when (val current = state) {
                    is TrailerState.Loading -> LoadingBox()
                    is TrailerState.Ready -> key(reloadTick, useMobilePage) {
                        when (val source = current.source) {
                            is TrailerSource.YouTube -> PlayerWebView(
                                loadUrl = if (useMobilePage) {
                                    "https://m.youtube.com/watch?v=${source.videoId}"
                                } else {
                                    TrailerLink.embedUrl(source.videoId)
                                },
                                // 内嵌页没有 Referer 时 YouTube 会报 Error 153，补一个
                                extraHeaders = if (useMobilePage) {
                                    emptyMap()
                                } else {
                                    mapOf("Referer" to "https://www.youtube.com/")
                                },
                                allowNavigation = useMobilePage,
                                onLoaded = { playerLoaded = true },
                                onProblem = { playerProblem = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                            is TrailerSource.Direct -> PlayerWebView(
                                html = directVideoHtml(source.url),
                                onLoaded = { playerLoaded = true },
                                onProblem = { playerProblem = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                            is TrailerSource.Web -> PlayerWebView(
                                loadUrl = source.url,
                                allowNavigation = true,
                                onLoaded = { playerLoaded = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is TrailerState.NotFound -> NotFoundBox(current.message, onRetry = vm::resolve)
                }
            }

            val problem = playerProblem
            if (ready != null && problem != null) {
                PlayerProblemCard(
                    message = problem,
                    externalLabel = if (ready.source is TrailerSource.YouTube) "在 YouTube 播放" else "浏览器打开",
                    onRetry = {
                        playerProblem = null
                        playerLoaded = false
                        reloadTick += 1
                    },
                    onBrowser = { playExternally(context, ready) },
                    onBilibili = { openExternal(context, bilibiliSearchUrl(item?.title)) },
                )
            } else if (ready != null && !playerLoaded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "正在加载播放器…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(item?.title ?: "预告片", style = MaterialTheme.typography.titleLarge)
                if (ready != null) {
                    Text(
                        ready.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "预告片来自 TMDB / YouTube，需要能访问 YouTube 的网络。打不开可以用下面的方式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val youtubeId = (ready?.source as? TrailerSource.YouTube)?.videoId
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ready?.let { playExternally(context, it) } }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (youtubeId != null) "在 YouTube 播放" else "浏览器打开")
                    }
                    OutlinedButton(onClick = { openExternal(context, bilibiliSearchUrl(item?.title)) }) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("B 站找预告")
                    }
                }
                if (youtubeId != null) {
                    TextButton(onClick = {
                        useMobilePage = !useMobilePage
                        playerProblem = null
                        playerLoaded = false
                    }) {
                        Text(if (useMobilePage) "改用内嵌播放器" else "内嵌播放失败？改用手机版网页播放")
                    }
                }
                item?.let { current ->
                    TextButton(onClick = { onEdit(current.id) }) {
                        Text("去编辑页填写视频直链（mp4）")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "正在查找预告片…",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NotFoundBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("重新查找")
        }
    }
}

@Composable
private fun PlayerProblemCard(
    message: String,
    externalLabel: String,
    onRetry: () -> Unit,
    onBrowser: () -> Unit,
    onBilibili: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "播放器没有加载出来",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onBrowser) { Text(externalLabel) }
                TextButton(onClick = onBilibili) { Text("B 站找预告") }
            }
        }
    }
}

/** 播放器 WebView：支持 YouTube 内嵌、mp4 直链与网页，并接管 HTML5 全屏 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PlayerWebView(
    modifier: Modifier = Modifier,
    loadUrl: String? = null,
    html: String? = null,
    allowNavigation: Boolean = false,
    extraHeaders: Map<String, String> = emptyMap(),
    onLoaded: () -> Unit = {},
    onProblem: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var pageFinished by remember { mutableStateOf(false) }

    val webView = remember(context, allowNavigation) {
        // 调试包打开远程调试，可用 chrome://inspect 查看 WebView 里的真实报错
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                // 去掉 WebView 的 "wv" 标记，避免部分站点把内嵌浏览器判为不支持
                userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    pageFinished = true
                    onLoaded()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        onProblem("无法加载页面：${error.description}")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        onProblem("播放器返回 HTTP ${errorResponse.statusCode}")
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (allowNavigation) return false
                    val target = request.url.toString()
                    // 只允许 YouTube / Google 站内跳转（含同意页），其余链接一律不跟随
                    val allowed = allowedHost(target)
                    return !allowed
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    fullscreenView = view
                    fullscreenCallback = callback
                }

                override fun onHideCustomView() {
                    fullscreenView = null
                    fullscreenCallback = null
                }
            }
        }
    }

    LaunchedEffect(loadUrl, html) {
        when {
            loadUrl != null -> webView.loadUrl(loadUrl, extraHeaders)
            html != null -> webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    // 看门狗：页面 15 秒还没加载完，基本就是网络到不了 YouTube
    LaunchedEffect(loadUrl, html) {
        if (loadUrl == null) return@LaunchedEffect
        delay(15_000)
        if (!pageFinished) {
            onProblem("页面 15 秒内没有加载完成，通常是网络无法访问 YouTube。")
        }
    }

    // 看门狗：页面加载完成后 12 秒还没真正开始播放，就提示网络问题（YouTube 被墙时就是这个表现）
    LaunchedEffect(pageFinished, loadUrl, html, allowNavigation) {
        if (!pageFinished || allowNavigation) return@LaunchedEffect
        delay(12_000)
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');return !!(v&&(v.readyState>2||v.currentTime>0||!v.paused));})()"
        ) { value ->
            if (value == "false") {
                onProblem("视频在 12 秒内没有开始播放，通常是网络无法访问 YouTube。")
            }
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)

    DisposableEffect(activity, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    // 退到后台时确保视频停住，避免只剩声音
                    webView.evaluateJavascript(
                        "(function(){var v=document.querySelector('video');if(v){v.pause();}})()",
                        null
                    )
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    val currentFullscreen = fullscreenView
    if (currentFullscreen != null) {
        Dialog(
            onDismissRequest = {
                (currentFullscreen.parent as? ViewGroup)?.removeView(currentFullscreen)
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (currentFullscreen.parent as? ViewGroup)?.removeView(currentFullscreen)
                            addView(
                                currentFullscreen,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** 允许在播放器内跳转的域名（YouTube 站内 + 同意页） */
private fun allowedHost(url: String): Boolean {
    if (url.startsWith("about:")) return true
    val host = Uri.parse(url).host?.lowercase() ?: return false
    return host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "youtu.be" ||
        host == "youtube-nocookie.com" ||
        host.endsWith(".youtube-nocookie.com") ||
        host == "google.com" ||
        host.endsWith(".google.com")
}

/** B 站搜索“片名 预告片”，网络没有 YouTube 时的兜底入口 */
private fun bilibiliSearchUrl(title: String?): String {
    val keyword = "${title.orEmpty()} 预告片".trim()
    return "https://search.bilibili.com/all?keyword=" + URLEncoder.encode(keyword, "UTF-8")
}

/** mp4 / m3u8 直链用一个极简 HTML 播放器承载 */
private fun directVideoHtml(url: String): String {
    val safe = url
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            html, body { margin: 0; padding: 0; height: 100%; background: #000; }
            video { width: 100%; height: 100%; object-fit: contain; }
          </style>
        </head>
        <body>
          <video src="$safe" controls autoplay playsinline preload="metadata"></video>
        </body>
        </html>
    """.trimIndent()
}

/** 用外部应用播放：YouTube 源直达 YouTube App，其他源交给浏览器 */
private fun playExternally(context: Context, ready: TrailerState.Ready) {
    val youtubeId = (ready.source as? TrailerSource.YouTube)?.videoId
    if (youtubeId != null) openYouTube(context, youtubeId) else openExternal(context, ready.externalUrl)
}

private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    val chooser = Intent.createChooser(intent, "选择应用打开")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "没有可以打开该链接的应用", Toast.LENGTH_SHORT).show()
    }
}
