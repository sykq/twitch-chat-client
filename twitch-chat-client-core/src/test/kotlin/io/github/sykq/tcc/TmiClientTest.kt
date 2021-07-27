package io.github.sykq.tcc

import mu.KotlinLogging
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

private val LOG = KotlinLogging.logger {}

@Disabled
internal class TmiClientTest {

    @Test
//    @Disabled
    internal fun test() {
        val tmiClient = tmiClient {
//            channels += "sykq"
//            channels += "codemiko"
            channels += "sunglitters"
//            channels += "harrie"
//            channels += "dumbdog"
            onConnect {
                LOG.warn("connected!!!!!")
                tagCapabilities()
//                textMessage(joinedChannels[0], "connected")
//                clearChat("sykq")
//                textMessage("sykq", "Hi test")
//                textMessage("sykq", "<3")
            }
            onMessage {
                println(it)
//                println("MESSAGE=${it.text} of type=${it.type} received at ${it.timestamp}")
//                if (message.text == "!hello") {
//                    session.textMessage(message.channel, "Hi ${text.user}!")
//                }
//                if (it.text  == "!emoteonly"){
//                    emoteOnly(it.channel)
//                }
//                if (it.text == "!emoteonlyoff"){
//                    emoteOnlyOff(it.channel)
//                }
            }
        }
        tmiClient.block()
    }

    @Test
//    @Disabled
    internal fun testWithPublisher() {
        val tmiClient = tmiClient {
            channels += "sykq"
        }

        tmiClient.blockWithPublisher(onConnect = {
            it.send(Mono.just(it.textMessage("PRIVMSG #sykq :connected")))
        },
            onMessage = { _, message ->
                println("MESSAGE=${message.payloadAsText}")
                Mono.empty()
            }
        )
    }

    @Test
    internal fun testReceive() {
        val tmiClient = tmiClient {
            channels += "sykq"
            onConnect {
                textMessage("sykq", "connected with receive()")
            }
        }

        tmiClient.receive { messageFlux ->
            messageFlux.filter { it.text == "test" }
                .doOnNext {
                    println("$it received")
                }
                .then()
        }.block()
    }

    @Test
    internal fun testReceiveWithSession() {
        val tmiClient = tmiClient {
            channels += "sykq"
            onConnect {
                textMessage("sykq", "connected with receive()")
            }
        }

        tmiClient.receiveWithSession { session, messageFlux ->
            messageFlux.filter { it.text == "test" }
                .doOnNext {
                    println("$it received")

                }
                .flatMap {
                    // TODO: the sending/consummation of actions needs a better api for this purpose
                    session.textMessage("sykq", "test received")
                    session.webSocketSession.send(session.consumeActions())
                }
//                .flatMap {
//                    session.webSocketSession.send(
//                        session.webSocketSession.textMessage("PRIVMSG #sykq :test received").toMono()
//                    )
//                }
                .then()
        }.block()
    }
}