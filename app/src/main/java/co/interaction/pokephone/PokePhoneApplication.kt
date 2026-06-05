package co.interaction.pokephone

import android.app.Application
import co.interaction.pokephone.notify.NotificationHelper

class PokePhoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
