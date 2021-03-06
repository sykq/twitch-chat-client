package io.github.sykq.tcc.bot

import io.github.sykq.tcc.ConnectionParametersProvider
import io.github.sykq.tcc.TmiClient
import io.github.sykq.tcc.TmiProperties
import io.github.sykq.tcc.tmiClient
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.concurrent.thread

private val LOG = KotlinLogging.logger {}

/**
 * Manages [BotBase] beans of this application.
 *
 * E.g. resolves the [TmiClient] to be used by each bot and connects them to the TMI.
 */
class BotRegistry(
    bots: List<BotBase>,
    connectionParametersProviders: List<ConnectionParametersProvider>,
    private val tmiProperties: TmiProperties,
) {
    private val tmiClients = resolveTmiClients(bots, connectionParametersProviders)
    val bots = bots.associateBy { it.name }
    val botNames
        get() = bots.keys.toList()

    init {
        bots.filter { it.autoConnect }
            .mergeAndConnect()
    }

    fun connect(botName: String): Mono<Void> =
        bots[botName]?.let {
            val tmiClient = tmiClients[it.name]!!
            prepareTmiClientInvocation(it, tmiClient)
        } ?: Mono.empty<Void>().also {
            LOG.warn { "could not find a bot with name $botName. Therefore, no connection has been established." }
        }

    fun connectAll(): List<Mono<Void>> = bots.values.mergeAndConnect()

    /**
     * Get all Bots which are assignable from the given [type].
     *
     * @param type the type of the bots to return.
     */
    fun getBotsByType(type: Class<out BotBase>): List<BotBase> =
        bots.values.filter { it::class.java.isAssignableFrom(type) }

    private fun prepareTmiClientInvocation(botBase: BotBase, tmiClient: TmiClient): Mono<Void> {
        val ircCapabilities = tmiProperties.bots.find { it.name == botBase.name }?.defaultCapabilities
            ?: tmiProperties.defaultCapabilities
        val capabilitiesToActivate = ircCapabilities.mapNotNull {
            it.onConnectAction
        }

        return when (botBase) {
            is Bot -> tmiClient.connect(
                { session ->
                    capabilitiesToActivate.forEach { it(session) }
                    botBase.onConnect(session)
                },
                { message -> botBase.onMessage(this, message) })
            is PublishingBot -> botBase.receive(tmiClient, capabilitiesToActivate)
        }
    }

    private fun resolveTmiClients(
        bots: List<BotBase>,
        connectionParametersProviders: List<ConnectionParametersProvider>
    ): Map<String, TmiClient> {
        val keyedConnectionParametersProvider = connectionParametersProviders.associateBy { it.botName }
        return bots.associate {
            it.name to resolveTmiClient(it, keyedConnectionParametersProvider)
        }
    }

    private fun resolveTmiClient(
        bot: BotBase,
        connectionParametersProviders: Map<String, ConnectionParametersProvider>
    ): TmiClient {
        if (bot.tmiClient != null) {
            return bot.tmiClient!!
        }

        return connectionParametersProviders.getConnectionParametersProvider(bot.name).let {
            val connectionParameters = it.getConnectionParameters()
            tmiClient {
                username = connectionParameters.username
                password = connectionParameters.password
                channels += bot.channels
            }
        }
    }

    private fun Map<String, ConnectionParametersProvider>.getConnectionParametersProvider(botName: String): ConnectionParametersProvider {
        return this[botName] ?: this["*"] ?: throw Exception(
            "no ConnectionParametersProvider found for bot with name $botName and no default provider registered. " +
                    "Either define a bean of such type for the given bot or set according tmi prefixed properties."
        )
    }

    private fun Collection<BotBase>.mergeAndConnect() =
        map { connect(it.name) }
            .also {
                thread {
                    Flux.merge(it)
                        .blockLast()
                }
            }

}