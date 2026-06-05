package co.interaction.pokephone.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

class PokeVoiceInteractionService : VoiceInteractionService() {
    override fun onLaunchVoiceAssistFromKeyguard() {
        showSession(Bundle().apply { putString("source", "keyguard") }, VoiceInteractionSession.SHOW_WITH_ASSIST)
    }
}
