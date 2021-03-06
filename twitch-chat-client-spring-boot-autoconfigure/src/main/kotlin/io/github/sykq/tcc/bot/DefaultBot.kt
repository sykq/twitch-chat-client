package io.github.sykq.tcc.bot

import io.github.sykq.tcc.ConfigurableTmiSession
import io.github.sykq.tcc.TmiClient
import io.github.sykq.tcc.TmiMessage
import io.github.sykq.tcc.TmiSession
import java.util.*

/**
 * Creates a new [Bot].
 *
 * The instance to be created can be configured by using an according [Bot.Configurer].
 */
fun defaultBot(configure: Bot.Configurer<DefaultBot>.() -> Unit): DefaultBot {
    val configurer = Bot.Configurer<DefaultBot>()
    configure(configurer)
    return DefaultBot(configurer)
}

/**
 * A default implementation of a [Bot].
 *
 * Can be configured by using an according [Bot.Configurer].
 */
class DefaultBot internal constructor(configurer: Bot.Configurer<DefaultBot>) : Bot {
    override val tmiClient: TmiClient = configurer.tmiClient!!

    override val name: String = configurer.name ?: UUID.randomUUID().toString()
    private val initialize: DefaultBot.() -> Unit = configurer.initialize
    private val channelsToJoin: List<String> = configurer.channels
    private val onConnectActions: List<ConfigurableTmiSession.() -> Unit> = listOf(configurer.onConnect)
    private val onMessageActions: List<TmiSession.(TmiMessage) -> Unit> = configurer.onMessageActions
    private val beforeShutdown: DefaultBot.() -> Unit = configurer.beforeShutdown

    override fun initialize() {
        initialize(this)
    }

    override fun onConnect(session: ConfigurableTmiSession) {
        onConnectActions.forEach { it(session) }
    }

    override fun onMessage(session: TmiSession, message: TmiMessage) {
        onMessageActions.forEach { it(session, message) }
    }

    override fun beforeShutdown() {
        beforeShutdown(this)
    }
}