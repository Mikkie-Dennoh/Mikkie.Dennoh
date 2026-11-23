package co.tawipay.topupdesk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

fun launchSimpleUssd(
    context: Context,
    code: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    telephonyManager.sendUssdRequest(
        code,
        object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(manager: TelephonyManager, request: String, response: CharSequence) {
                onResult(response.toString())
            }

            override fun onReceiveUssdResponseFailed(manager: TelephonyManager, request: String, failureCode: Int) {
                onError("USSD request failed (code $failureCode). Check the SIM, network signal, and code.")
            }
        },
        Handler(Looper.getMainLooper()),
    )
}

fun launchAdvancedUssd(context: Context, sequence: UssdSequence) {
    AppPreferences(context).stageReplies(sequence.replies)
    val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", sequence.firstCode, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
