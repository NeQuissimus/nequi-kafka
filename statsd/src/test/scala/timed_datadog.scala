package nequi.kafka.streams.statsd

import github.gphat.censorinus._

import net.manub.embeddedkafka._
import net.manub.embeddedkafka.Codecs._
import net.manub.embeddedkafka.ConsumerExtensions._
import net.manub.embeddedkafka.streams._

import org.apache.kafka.streams.scala._

import utest._
import utest.framework.TestPath

object TimedDataDogSpec extends TestSuite with EmbeddedKafkaStreamsAllInOne {
  import ImplicitConversions._
  import Serdes.String
  import imports._

  def dynConfig() = EmbeddedKafkaConfig(kafkaPort = getEmptyPort, zooKeeperPort = getEmptyPort)

  val tests = Tests {
    'timeOne - timeOne
    'timeTwo - timeTwo
  }

  def newClient() = new TestDataDogClient

  def timeOne(implicit path: TestPath) = {
    implicit val c = dynConfig()
    val inTopic    = pathToTopic("-in")
    val outTopic   = pathToTopic("-out")
    val client     = newClient()
    val tags       = Seq(pathToTopic("1"), pathToTopic("2"))

    val builder = new StreamsBuilder
    val in      = builder.stream[String, String](inTopic)

    val out = in.timed[String, String, String](builder, client, "timed")((k, v) => k, (k, v) => k, tags) { stream =>
      stream.mapValues(v => {
        Thread.sleep(1000L)
        v
      })
    }

    out.to(outTopic)

    runStreams(Seq(inTopic, outTopic), builder.build()) {
      publishToKafka(inTopic, "hello", "world")

      withConsumer[String, String, Unit] { consumer =>
        val consumedMessages: Stream[(String, String)] = consumer.consumeLazily(outTopic)
        consumedMessages.head ==> ("hello" -> "world")
      }
    }

    client.q.size ==> 1
    val m = client.q.poll

    assertMatch(m) {
      case TimerMetric(name, value, _, t) if name == "timed" && value >= 1000L && t == tags =>
    }
  }

  def timeTwo(implicit path: TestPath) = {
    implicit val c = dynConfig()
    val inTopic    = pathToTopic("-in")
    val outTopic   = pathToTopic("-out")
    val client     = newClient()
    val tags       = Seq(pathToTopic("1"), pathToTopic("2"))

    val builder = new StreamsBuilder
    val in      = builder.stream[String, String](inTopic)

    val out = in.timed[String, String, String](builder, client, "timed")((k, v) => k, (k, v) => k, tags) { stream =>
      stream.mapValues(v => {
        Thread.sleep(1000L)
        v
      })
    }

    out.to(outTopic)

    runStreams(Seq(inTopic, outTopic), builder.build()) {
      publishToKafka(inTopic, "hello", "world")
      publishToKafka(inTopic, "hello", "world")

      withConsumer[String, String, Unit] { consumer =>
        val consumedMessages: Stream[(String, String)] = consumer.consumeLazily(outTopic)
        consumedMessages.head ==> ("hello" -> "world")
      }
    }

    client.q.size ==> 2
    val m = client.q.poll

    assertMatch(m) {
      case TimerMetric(name, value, _, t) if name == "timed" && value >= 1000L && t == tags =>
    }

    val m2 = client.q.poll

    assertMatch(m2) {
      case TimerMetric(name, value, _, t) if name == "timed" && value >= 1000L && t == tags =>
    }
  }
}
