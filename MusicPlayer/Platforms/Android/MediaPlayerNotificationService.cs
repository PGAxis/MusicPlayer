using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Media;
using Android.OS;
using Android.Support.V4.Media;
using Android.Support.V4.Media.Session;
using Android.Views;
using Android.Widget;
using AndroidX.Core.App;

namespace MusicPlayer
{
    [Service(Exported = true, ForegroundServiceType = ForegroundService.TypeMediaPlayback)]
    public class MediaPlayerNotificationService : Service
    {
        public const string CHANNEL_ID = "media_playback_channel";

        public const string ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION";

        private static bool Notification_started = false;

        public static MediaSessionCompat mediaSession;

        public static MediaPlayer mediaPlayer;

        private static Settings settings = Settings.Instance();

        public static bool IsPlaying = false;

        public override void OnCreate()
        {
            base.OnCreate();
            CreateNotificationChannel();

            mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
            mediaSession.SetCallback(new MediaSessionCallback());
            mediaSession.Active = true;
        }

        public override StartCommandResult OnStartCommand(Intent intent, StartCommandFlags flags, int startId)
        {

            var notification = BuildNotification();
            var notificationManager = NotificationManagerCompat.From(this);

            if (intent.Action == ACTION_UPDATE_NOTIFICATION)
            {
                notificationManager.Notify(16, notification);
                return StartCommandResult.Sticky;
            }

            if (Notification_started)
            {
                notificationManager.Notify(16, notification);
                return StartCommandResult.Sticky;
            }

            var metadata = new MediaMetadataCompat.Builder()
                .PutString(MediaMetadata.MetadataKeyTitle, settings.LastSongName)
                .PutString(MediaMetadata.MetadataKeyArtist, settings.LastSongArtist)
                .PutBitmap(MediaMetadata.MetadataKeyAlbumArt, BitmapFactory.DecodeFile(settings.LastSongCoverPath))
                .PutLong(MediaMetadata.MetadataKeyDuration, mediaPlayer.Duration)
                .Build();

            mediaSession.SetMetadata(metadata);

            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(PlaybackStateCompat.ActionSetShuffleMode |
                            PlaybackStateCompat.ActionSetRepeatMode |
                            PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious)
                .SetState(PlaybackStateCompat.StatePlaying, settings.LastSongTime, 1f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            notificationManager.Notify(16, notification);

            StartForeground(16, notification);

            Notification_started = true;

            IsPlaying = true;

            settings.Source = "pause.png";

            return StartCommandResult.Sticky;
        }

        Notification BuildNotification()
        {
            Intent intent = new Intent(this, typeof(MainActivity));
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
            PendingIntent pendingIntent = PendingIntent.GetActivity(this, 0, intent, PendingIntentFlags.Immutable);

            var builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .SetContentTitle(settings.LastSongName)
                .SetContentText(settings.LastSongArtist)
                .SetSmallIcon(Resource.Drawable.small_icon)
                .SetStyle(new AndroidX.Media.App.NotificationCompat.DecoratedMediaCustomViewStyle().SetMediaSession(mediaSession.SessionToken).SetShowActionsInCompactView(0, 1, 2))
                .AddAction(new NotificationCompat.Action(0, "Previous", CreateMediaButtonPendingIntent(this, Keycode.MediaPrevious)))
                .AddAction(new NotificationCompat.Action(0, "Play", CreateMediaButtonPendingIntent(this, Keycode.MediaPlayPause)))
                .AddAction(new NotificationCompat.Action(0, "Next", CreateMediaButtonPendingIntent(this, Keycode.MediaNext)))
                .SetOngoing(true)
                .SetAutoCancel(false)
                .SetContentIntent(pendingIntent)
                .SetPriority(NotificationCompat.PriorityLow)
                .SetVisibility(NotificationCompat.VisibilityPublic);

            return builder.Build();
        }

        private PendingIntent CreateMediaButtonPendingIntent(Context context, Keycode keyCode)
        {
            var intent = new Intent(Intent.ActionMediaButton);
            intent.SetPackage(context.PackageName);
            intent.PutExtra(Intent.ExtraKeyEvent, new KeyEvent(KeyEventActions.Down, keyCode));
            return PendingIntent.GetBroadcast(context, (int)keyCode, intent, PendingIntentFlags.UpdateCurrent | PendingIntentFlags.Immutable);
        }

        public static void Pause()
        {
            if (mediaPlayer != null && mediaPlayer.IsPlaying)
            {
                mediaPlayer.Pause();
                settings.LastSongTime = mediaPlayer.CurrentPosition;
                settings.SaveSettings();
                IsPlaying = false;
            }

            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(PlaybackStateCompat.ActionSetShuffleMode |
                            PlaybackStateCompat.ActionSetRepeatMode |
                            PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious
                )
                .SetState(PlaybackStateCompat.StatePaused, settings.LastSongTime, 1.0f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            settings.Source = "play.png";
        }

        public static void Play()
        {
            if (mediaPlayer != null && !mediaPlayer.IsPlaying)
            {
                mediaPlayer.SeekTo(settings.LastSongTime);
                mediaPlayer.Start();
                IsPlaying = true;
            }

            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(PlaybackStateCompat.ActionSetShuffleMode |
                            PlaybackStateCompat.ActionSetRepeatMode |
                            PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious
                )
                .SetState(PlaybackStateCompat.StatePlaying, settings.LastSongTime, 1.0f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            settings.Source = "pause.png";
        }

        void CreateNotificationChannel()
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
            {
                var channel = new NotificationChannel(CHANNEL_ID, "Media Playback", NotificationImportance.Low)
                {
                    Description = "Playback controls",
                    LockscreenVisibility = NotificationVisibility.Public
                };

                channel.EnableVibration(false);
                channel.SetSound(null, null);
                channel.SetShowBadge(false);
                var manager = (NotificationManager)GetSystemService(NotificationService);
                manager.CreateNotificationChannel(channel);
            }
        }

        public override IBinder OnBind(Intent intent) => null;

        public static void StartPlayback(string path)
        {
            if (mediaPlayer != null)
            {
                mediaPlayer.Release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.SetDataSource(path);
            mediaPlayer.Prepared += (s, e) =>
            {
                mediaPlayer.SeekTo(settings.LastSongTime);
                mediaPlayer.Start();
            };
            mediaPlayer.PrepareAsync();

            settings.LastSongPath = path;
            IsPlaying = true;
        }
    }

    class MediaSessionCallback : MediaSessionCompat.Callback
    {
        public override void OnPlay()
        {
            var context = Android.App.Application.Context;
            MediaPlayerNotificationService.Play();
        }
        public override void OnPause()
        {
            var context = Android.App.Application.Context;
            MediaPlayerNotificationService.Pause();
        }
        public override void OnSkipToNext()
        {
            var context = Android.App.Application.Context;
            Toast.MakeText(context, "Action: Next", ToastLength.Short).Show();
        }
        public override void OnSkipToPrevious()
        {
            var context = Android.App.Application.Context;
            Toast.MakeText(context, "Action: Previous", ToastLength.Short).Show();
        }
    }
}
