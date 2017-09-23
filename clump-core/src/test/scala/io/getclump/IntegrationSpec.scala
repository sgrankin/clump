package io.getclump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Spec {
  val tweetRepository        = new TweetRepository
  val userRepository         = new UserRepository
  val zipUserRepository      = new ZipUserRepository
  val filteredUserRepository = new FilteredUserRepository
  val timelineRepository     = new TimelineRepository
  val likeRepository         = new LikeRepository
  val trackRepository        = new TrackRepository
  val topTracksRepository    = new TopTracksRepository
  val failingTweetRepository = new FailingTweetRepository

  val tweets        = Clump.source(tweetRepository.tweetsFor _)
  val users         = Clump.source(userRepository.usersFor _)
  val filteredUsers = Clump.source(filteredUserRepository.usersFor _)(_.userId)
  val zippedUsers   = Clump.sourceZip(zipUserRepository.usersFor _)
  val timelines     = Clump.source(timelineRepository.timelinesFor _)(_.timelineId)
  val likes         = Clump.source(likeRepository.likesFor _)(_.likeId)
  val tracks        = Clump.source(trackRepository.tracksFor _)(_.trackId)

  "A Clump should batch calls to services" in {
    val tweetRepositoryMock = mock[TweetRepository]
    val tweets              = Clump.source(tweetRepositoryMock.tweetsFor _)

    val userRepositoryMock = mock[UserRepository]
    val users              = Clump.source(userRepositoryMock.usersFor _)
    val topTracks          = Clump.sourceSingle(topTracksRepository.topTracksFor _)

    tweetRepositoryMock.tweetsFor(Set(1L, 2L, 3L)) returns
      Future.successful(
        Map(
          1L -> Tweet("Tweet1", 10),
          2L -> Tweet("Tweet2", 20),
          3L -> Tweet("Tweet3", 30)
        ))

    userRepositoryMock.usersFor(Set(10L, 20L, 30L)) returns
      Future.successful(
        Map(
          10L -> User(10, "User10"),
          20L -> User(20, "User20"),
          30L -> User(30, "User30")
        ))

    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet  <- tweets.get(tweetId)
        user   <- users.get(tweet.userId)
        tracks <- topTracks.get(user)
      } yield (tweet, user, tracks)
    }

    awaitResult(enrichedTweets.get) ==== Some(
      List(
        (Tweet("Tweet1", 10),
         User(10, "User10"),
         Set(Track(10, "Track10"), Track(11, "Track11"), Track(12, "Track12"))),
        (Tweet("Tweet2", 20),
         User(20, "User20"),
         Set(Track(20, "Track20"), Track(21, "Track21"), Track(22, "Track22"))),
        (Tweet("Tweet3", 30), User(30, "User30"), Set(Track(30, "Track30"), Track(31, "Track31"), Track(32, "Track32")))
      ))
  }

  "A Clump should batch calls to parameterized services" in {
    val parameterizedTweetRepositoryMock = mock[ParameterizedTweetRepository]
    val tweets                           = Clump.source(parameterizedTweetRepositoryMock.tweetsFor _)

    val parameterizedUserRepositoryMock = mock[ParameterizedUserRepository]
    val users                           = Clump.source(parameterizedUserRepositoryMock.usersFor _)(_.userId)

    parameterizedTweetRepositoryMock.tweetsFor("foo", Set(1, 2, 3)) returns
      Future.successful(
        Map(
          1L -> Tweet("Tweet1", 10),
          2L -> Tweet("Tweet2", 20),
          3L -> Tweet("Tweet3", 30)
        ))

    parameterizedUserRepositoryMock.usersFor("bar", Set(10, 20, 30)) returns
      Future.successful(
        Set(
          User(10, "User10"),
          User(20, "User20"),
          User(30, "User30")
        ))

    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get("foo", tweetId)
        user  <- users.get("bar", tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.get) ==== Some(
      List((Tweet("Tweet1", 10), User(10, "User10")),
           (Tweet("Tweet2", 20), User(20, "User20")),
           (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "it should be able to be used in complex nested fetches" in {
    val timelineIds = List(1, 3)
    val enrichedTimelines = Clump.traverse(timelineIds) { id =>
      for {
        timeline <- timelines.get(id)
        enrichedLikes <- Clump.traverse(timeline.likeIds) { id =>
          for {
            like            <- likes.get(id)
            (tracks, users) <- tracks.get(like.trackIds).join(users.get(like.userIds))
          } yield (like, tracks, users)
        }
      } yield (timeline, enrichedLikes)
    }

    awaitResult(enrichedTimelines.get) ==== Some(
      List(
        (Timeline(1, List(10, 20)),
         List(
           (Like(10, List(100, 200), List(1000, 2000)),
            List(Track(100, "Track100"), Track(200, "Track200")),
            List(User(1000, "User1000"), User(2000, "User2000"))),
           (Like(20, List(200, 400), List(2000, 4000)),
            List(Track(200, "Track200"), Track(400, "Track400")),
            List(User(2000, "User2000"), User(4000, "User4000")))
         )),
        (Timeline(3, List(30, 60)),
         List(
           (Like(30, List(300, 600), List(3000, 6000)),
            List(Track(300, "Track300"), Track(600, "Track600")),
            List(User(3000, "User3000"), User(6000, "User6000"))),
           (Like(60, List(600, 1200), List(6000, 12000)),
            List(Track(600, "Track600"), Track(1200, "Track1200")),
            List(User(6000, "User6000"), User(12000, "User12000")))
         ))
      ))
  }

  "it should be usable with regular maps and flatMaps" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Clump[List[(Tweet, User)]] =
      Clump.traverse(tweetIds) { tweetId =>
        tweets.get(tweetId).flatMap(tweet => users.get(tweet.userId).map(user => (tweet, user)))
      }

    awaitResult(enrichedTweets.get) ==== Some(
      List((Tweet("Tweet1", 10), User(10, "User10")),
           (Tweet("Tweet2", 20), User(20, "User20")),
           (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "it should allow unwrapping Clumped lists with clump.list" in {
    val enrichedTweets: Clump[List[(Tweet, User)]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user  <- users.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.list) ==== List((Tweet("Tweet1", 10), User(10, "User10")),
                                               (Tweet("Tweet2", 20), User(20, "User20")),
                                               (Tweet("Tweet3", 30), User(30, "User30")))
  }

  "it should work with Clump.sourceZip" in {
    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user  <- zippedUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.get) ==== Some(
      List((Tweet("Tweet1", 10), User(10, "User10")),
           (Tweet("Tweet2", 20), User(20, "User20")),
           (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "A Clump can have a partial result" in {
    val onlyFullObjectGraph: Clump[List[(Tweet, User)]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user  <- filteredUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(onlyFullObjectGraph.get) ==== Some(List((Tweet("Tweet2", 20), User(20, "User20"))))

    val partialResponses: Clump[List[(Tweet, Option[User])]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user  <- filteredUsers.get(tweet.userId).optional
      } yield (tweet, user)
    }

    awaitResult(partialResponses.get) ==== Some(
      List((Tweet("Tweet1", 10), None), (Tweet("Tweet2", 20), Some(User(20, "User20"))), (Tweet("Tweet3", 30), None)))
  }

  "A Clump can have individual failing entities" in {
    val source = Clump.sourceTry(failingTweetRepository.tweetsFor _)

    def getTweetWithoutFallback(id: Long) = source.get(id)
    def getTweetWithFallback(id: Long)    = source.get(id).fallback(Tweet("<error>", 0))

    val fail = for {
      tweetA <- getTweetWithoutFallback(1)
      tweetB <- getTweetWithoutFallback(-1)
    } yield (tweetA, tweetB)

    awaitResult(fail.get) must throwA[IllegalStateException]

    val success = for {
      tweetA <- getTweetWithFallback(1)
      tweetB <- getTweetWithFallback(-1)
    } yield (tweetA, tweetB)

    awaitResult(success.get) ==== Some((Tweet("Tweet1", 10), Tweet("<error>", 0)))
  }
}

case class Tweet(body: String, userId: Long)

case class User(userId: Long, name: String)

case class Timeline(timelineId: Int, likeIds: List[Long])

case class Like(likeId: Long, trackIds: List[Long], userIds: List[Long])

case class Track(trackId: Long, name: String)

class TweetRepository {
  def tweetsFor(ids: Set[Long]): Future[Map[Long, Tweet]] = {
    Future.successful(ids.map(id => id -> Tweet(s"Tweet$id", id * 10)).toMap)
  }
}

trait ParameterizedTweetRepository {
  def tweetsFor(prefix: String, ids: Set[Long]): Future[Map[Long, Tweet]]
}

class FailingTweetRepository {
  def tweetsFor(ids: Set[Long]): Future[Map[Long, Try[Tweet]]] = {
    Future.successful(ids.map {
      case id if id > 0 => id -> Success(Tweet(s"Tweet$id", id * 10))
      case id           => id -> Failure(new IllegalStateException)
    }.toMap)
  }
}

class UserRepository {
  def usersFor(ids: Set[Long]): Future[Map[Long, User]] = {
    Future.successful(ids.map(id => id -> User(id, s"User$id")).toMap)
  }
}

trait ParameterizedUserRepository {
  def usersFor(prefix: String, ids: Set[Long]): Future[Set[User]]
}

class ZipUserRepository {
  def usersFor(ids: List[Long]): Future[List[User]] = {
    Future.successful(ids.map(id => User(id, s"User$id")))
  }
}

class FilteredUserRepository {
  def usersFor(ids: Set[Long]): Future[Set[User]] = {
    Future.successful(ids.filter(_ % 20 == 0).map(id => User(id, s"User$id")))
  }
}

class TimelineRepository {
  def timelinesFor(ids: Set[Int]): Future[Set[Timeline]] = {
    Future.successful(ids.map(id => Timeline(id, List(id * 10, id * 20))))
  }
}

class LikeRepository {
  def likesFor(ids: Set[Long]): Future[Set[Like]] = {
    Future.successful(ids.map(id => Like(id, List(id * 10, id * 20), List(id * 100, id * 200))))
  }
}

class TrackRepository {
  def tracksFor(ids: Set[Long]): Future[Set[Track]] = {
    Future.successful(ids.map(id => Track(id, s"Track$id")))
  }
}

class TopTracksRepository {
  def topTracksFor(user: User): Future[Set[Track]] = {
    def track(id: Long): Track = Track(id, s"Track$id")
    val userId                 = user.userId
    Future.successful(Set(track(userId), track(userId + 1), track(userId + 2)))
  }
}
