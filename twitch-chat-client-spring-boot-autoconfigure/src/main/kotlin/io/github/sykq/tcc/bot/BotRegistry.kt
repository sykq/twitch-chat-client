package io.github.sykq.tcc.bot

import io.github.sykq.tcc.ConnectionParametersProvider
import io.github.sykq.tcc.TmiClient
import io.github.sykq.tcc.tmiClient
import mu.KotlinLogging
import kotlin.concurrent.thread

private val LOG = KotlinLogging.logger {}

class BotRegistry(
    bots: List<BotBase>,
    connectionParametersProviders: List<ConnectionParametersProvider>
) {
    private val tmiClients = resolveTmiClients(bots, connectionParametersProviders)
    val bots = bots.associateBy { it.name }
    val botNames
        get() = bots.keys.toList()

    init {
        bots.filter {
            it.autoConnect
        }.forEach { connect(it.name) }
    }

    fun connect(botName: String) {
        thread {
            bots[botName]?.let {
                val tmiClient = tmiClients[it.name]!!
                prepareTmiClientInvocation(it, tmiClient)
            } ?: LOG.warn { "could not find a bot with name $botName. Therefore, no connection has been established." }
        }
    }

    fun connectAll() {
        bots.forEach {
            val tmiClient = tmiClients[it.key]!!
            thread {
                prepareTmiClientInvocation(it.value, tmiClient)
            }
        }
    }

    /**
     * Get all Bots which are assignable from the given [type].
     *
     * @param type the type of the bots to return.
     */
    fun getBotsByType(type: Class<out BotBase>): List<BotBase> = bots.values.filter { it::class.java.isAssignableFrom(type) }

    private fun prepareTmiClientInvocation(botBase: BotBase, tmiClient: TmiClient) {
        when (botBase) {
            is Bot -> tmiClient.block({ session -> botBase.onConnect(session) },
                { message -> botBase.onMessage(this, message) })
            is ReactiveBot -> botBase.receive(tmiClient)
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

}