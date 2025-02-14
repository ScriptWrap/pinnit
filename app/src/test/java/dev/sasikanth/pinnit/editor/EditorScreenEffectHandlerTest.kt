package dev.sasikanth.pinnit.editor

import com.spotify.mobius.Connection
import com.spotify.mobius.test.RecordingConsumer
import dev.sasikanth.pinnit.data.ScheduleType
import dev.sasikanth.pinnit.editor.ScheduleValidator.Result.Valid
import dev.sasikanth.pinnit.notifications.NotificationRepository
import dev.sasikanth.pinnit.scheduler.PinnitNotificationScheduler
import dev.sasikanth.pinnit.utils.TestDispatcherProvider
import dev.sasikanth.pinnit.utils.notification.NotificationUtil
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class EditorScreenEffectHandlerTest {

  private val testScope = TestScope()

  private val viewEffectConsumer = RecordingConsumer<EditorScreenViewEffect>()
  private val repository = mock<NotificationRepository>()
  private val dispatcherProvider = TestDispatcherProvider()
  private val notificationUtil = mock<NotificationUtil>()
  private val pinnitNotificationScheduler = mock<PinnitNotificationScheduler>()
  private val scheduleValidator = mock<ScheduleValidator>()
  private val effectHandler = EditorScreenEffectHandler(
    repository,
    dispatcherProvider,
    notificationUtil,
    pinnitNotificationScheduler,
    scheduleValidator,
    viewEffectConsumer
  )

  private val consumer = RecordingConsumer<EditorScreenEvent>()
  private lateinit var connection: Connection<EditorScreenEffect>

  @Before
  fun setup() {
    connection = effectHandler.build().connect(consumer)
  }

  @After
  fun teardown() {
    connection.dispose()
  }

  @Test
  fun `when load notification effect is received, then load the notification`() = testScope.runTest {
    // given
    val notificationUuid = UUID.fromString("b44624c8-0535-4743-a97b-d0350fd446c2")
    val notification = dev.sasikanth.sharedtestcode.TestData.notification(
      uuid = UUID.fromString("999f6f57-8ddd-41c2-886d-78d2e1c9b0b8")
    )

    whenever(repository.notification(notificationUuid)) doReturn notification

    // when
    connection.accept(LoadNotification(notificationUuid))

    // then
    verify(repository).notification(notificationUuid)
    verifyNoMoreInteractions(repository)

    consumer.assertValues(NotificationLoaded(notification))
  }

  @Test
  fun `when save notification effect is received, then save the notification`() = testScope.runTest {
    // given
    val notificationUuid = UUID.fromString("9610e5b7-6894-4da9-965a-048abf568247")
    val title = "Notification Title"
    val content = "This is content"
    val schedule = dev.sasikanth.sharedtestcode.TestData.schedule(
      scheduleDate = LocalDate.parse("2020-01-01"),
      scheduleTime = LocalTime.parse("09:00:00"),
      scheduleType = ScheduleType.Daily
    )

    val notification = dev.sasikanth.sharedtestcode.TestData.notification(
      uuid = notificationUuid,
      title = title,
      content = content,
      isPinned = true
    )

    whenever(
      repository.save(
        title = eq(title),
        content = eq(content),
        isPinned = eq(true),
        schedule = eq(schedule),
        uuid = any()
      )
    ) doReturn notification

    // when
    connection.accept(SaveNotification(title, content, schedule, true))

    // then
    verify(repository).save(
      title = eq(title),
      content = eq(content),
      isPinned = eq(true),
      schedule = eq(schedule),
      uuid = any()
    )
    verifyNoMoreInteractions(repository)

    consumer.assertValues(NotificationSaved(notification))
    viewEffectConsumer.assertValues()
  }

  @Test
  fun `when update notification effect is received, then update the notification`() = testScope.runTest {
    // given
    val notificationUuid = UUID.fromString("4e91382a-d5c3-44a7-8ee3-fa15a4ec69b4")
    val notification = dev.sasikanth.sharedtestcode.TestData.notification(
      uuid = notificationUuid,
      title = "Notification Title"
    )

    val schedule = dev.sasikanth.sharedtestcode.TestData.schedule(
      scheduleDate = LocalDate.parse("2020-01-01"),
      scheduleTime = LocalTime.parse("09:00:00"),
      scheduleType = ScheduleType.Daily
    )

    val updatedTitle = "Updated Title"
    val updatedNotification = notification
      .copy(title = updatedTitle, schedule = schedule)

    whenever(repository.notification(notificationUuid)) doReturn notification
    whenever(repository.updateNotification(updatedNotification)) doReturn updatedNotification

    // when
    connection.accept(
      UpdateNotification(
        notificationUuid = notificationUuid,
        title = updatedTitle,
        content = null,
        schedule = schedule
      )
    )

    // then
    verify(repository).notification(notificationUuid)
    verify(repository).updateNotification(updatedNotification)
    verifyNoMoreInteractions(repository)

    consumer.assertValues(NotificationUpdated(updatedNotification))
    viewEffectConsumer.assertValues()
  }

  @Test
  fun `when close editor effect is received, then close the editor view`() = testScope.runTest {
    // when
    connection.accept(CloseEditor)

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(CloseEditorView)
  }

  @Test
  fun `when show confirm exit effect is received, then show confirm exit dialog`() = testScope.runTest {
    // when
    connection.accept(ShowConfirmExitEditor)

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(ShowConfirmExitEditorDialog)
  }

  @Test
  fun `when delete notification effect is received, then delete the notification`() = testScope.runTest {
    // give
    val notification = dev.sasikanth.sharedtestcode.TestData.notification(
      uuid = UUID.fromString("0e51b71a-2bec-49eb-bbec-1e5d1b74e643")
    )

    // when
    connection.accept(DeleteNotification(notification))

    // then
    verify(repository).updatePinStatus(notification.uuid, false)
    verify(repository).deleteNotification(notification)
    verifyNoMoreInteractions(repository)
    verify(notificationUtil).dismissNotification(notification)
    verifyNoMoreInteractions(notificationUtil)
    verify(pinnitNotificationScheduler).cancel(notification.uuid)
    verifyNoMoreInteractions(pinnitNotificationScheduler)

    consumer.assertValues()
    viewEffectConsumer.assertValues(CloseEditorView)
  }

  @Test
  fun `when show confirm delete effect is received, then display the confirm delete dialog`() = testScope.runTest {
    // when
    connection.accept(ShowConfirmDelete)

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(ShowConfirmDeleteDialog)
  }

  @Test
  fun `when set title and content effect is received, then set title and content`() = testScope.runTest {
    // given
    val notificationContent = "Notification Content"

    // when
    connection.accept(SetTitleAndContent(null, notificationContent))

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(SetTitle(null), SetContent(notificationContent))
  }

  @Test
  fun `when show date picker effect is received, then show date picker dialog`() = testScope.runTest {
    // given
    val date = LocalDate.parse("2020-01-01")

    // when
    connection.accept(ShowDatePicker(date))

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(ShowDatePickerDialog(date))
  }

  @Test
  fun `when show time picker effect is received, then show time picker dialog`() = testScope.runTest {
    // given
    val time = LocalTime.parse("09:00:00")

    // when
    connection.accept(ShowTimePicker(time))

    // then
    verifyNoInteractions(repository)
    verifyNoInteractions(notificationUtil)

    consumer.assertValues()
    viewEffectConsumer.assertValues(ShowTimePickerDialog(time))
  }

  @Test
  fun `when show notification effect is received, then show android notification`() = testScope.runTest {
    // given
    val notification = dev.sasikanth.sharedtestcode.TestData.notification(
      uuid = UUID.fromString("e3848c84-afe9-45a6-ba90-d7f0ad3de193")
    )

    // when
    connection.accept(ShowNotification(notification))

    // then
    consumer.assertValues()
    viewEffectConsumer.assertValues()

    verify(notificationUtil).showNotification(notification)
    verifyNoMoreInteractions(notificationUtil)
  }

  @Test
  fun `when schedule notification effect is received, then schedule a notification`() = testScope.runTest {
    // given
    val notification = dev.sasikanth.sharedtestcode.TestData.notification()

    // when
    connection.accept(ScheduleNotification(notification))

    // then
    consumer.assertValues()
    viewEffectConsumer.assertValues()

    verify(pinnitNotificationScheduler).scheduleNotification(notification)
    verifyNoMoreInteractions(pinnitNotificationScheduler)
  }

  @Test
  fun `when cancel notification schedule effect is received, then cancel notification schedule`() = testScope.runTest {
    // given
    val notificationId = UUID.fromString("43c61479-2529-424a-a0fa-12b4bd90f591")

    // when
    connection.accept(CancelNotificationSchedule(notificationId))

    // then
    consumer.assertValues()
    viewEffectConsumer.assertValues()

    verify(pinnitNotificationScheduler).cancel(notificationId)
    verifyNoMoreInteractions(pinnitNotificationScheduler)
  }

  @Test
  fun `when validate schedule effect is received, then validate schedule`() = testScope.runTest {
    // given
    val scheduleDate = LocalDate.parse("2020-01-01")
    val scheduleTime = LocalTime.parse("09:00:00")

    whenever(scheduleValidator.validate(scheduleDate, scheduleTime)) doReturn Valid

    // when
    connection.accept(ValidateSchedule(scheduleDate, scheduleTime))

    // then
    consumer.assertValues(ScheduleValidated(Valid))
    viewEffectConsumer.assertValues()
  }
}
