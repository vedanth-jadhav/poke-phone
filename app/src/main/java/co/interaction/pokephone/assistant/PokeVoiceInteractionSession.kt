package co.interaction.pokephone.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import co.interaction.pokephone.capture.CaptureActivity

class PokeVoiceInteractionSession(
    context: Context,
    private val launchArgs: Bundle?
) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val intent = Intent(context, CaptureActivity::class.java)
            .putExtra(CaptureActivity.EXTRA_SOURCE, launchArgs?.getString("source") ?: "assistant")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        runCatching {
            startAssistantActivity(intent)
        }.onFailure {
            context.startActivity(intent)
        }

        hide()
    }
}
