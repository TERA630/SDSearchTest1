package io.github.tera630.sdsearchtest1.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun FolderPickerButton(onPicked: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree() // どんなアクティビティ起動：ここではDocumentTreeでuri型のオブジェクトが返る。
    ) { uri ->                                              // アクティビティ終了時にここが呼ばれる。
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(it, flags)
            onPicked(it)
        }
    }
    Button(onClick = { launcher.launch(null) }) { Text("SDカードを選ぶ") }
}
