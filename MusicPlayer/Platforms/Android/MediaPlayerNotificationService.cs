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
using AndroidX.Media.Session;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    [Service(Exported = true, ForegroundServiceType = ForegroundService.TypeMediaPlayback)]
    public class MediaPlayerNotificationService : Service
    {
        public const string CHANNEL_ID = "media_playback_channel";

        public const string ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION";

        public static MediaSessionCompat mediaSession;

        public static bool IsPlaying = true;

        public override void OnCreate()
        {
            base.OnCreate();
            CreateNotificationChannel();

            mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
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

            var metadata = new MediaMetadataCompat.Builder()
                .PutString(MediaMetadata.MetadataKeyTitle, "Song Title")
                .PutString(MediaMetadata.MetadataKeyArtist, "Artist Name")
                .PutBitmap(MediaMetadata.MetadataKeyAlbumArt, BitmapFactory.DecodeResource(Resources, Resource.Drawable.default_playlist))
                .Build();

            mediaSession.SetMetadata(metadata);

            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious)
                .SetState(PlaybackStateCompat.StatePlaying, 0, 1f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            notificationManager.Notify(16, notification);

            StartForeground(16, notification);

            return StartCommandResult.Sticky;
        }

        Notification BuildNotification()
        {
            Intent intent = new Intent(this, typeof(MainActivity));
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
            PendingIntent pendingIntent = PendingIntent.GetActivity(this, 0, intent, PendingIntentFlags.Immutable);

            var builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .SetContentTitle("Song Name")
                .SetContentText("Song Artist")
                .SetSmallIcon(Resource.Drawable.small_icon)
                .SetStyle(new AndroidX.Media.App.NotificationCompat.DecoratedMediaCustomViewStyle().SetMediaSession(mediaSession.SessionToken).SetShowActionsInCompactView(0, 1, 2))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.previous, "Previous", GetActionIntent("PREVIOUS", 101)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.play, "Play", GetActionIntent(IsPlaying ? "PLAY" : "PAUSE", 102)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.next, "Next", GetActionIntent("NEXT", 103)))
                .SetOngoing(true)
                .SetAutoCancel(false)
                .SetContentIntent(pendingIntent)
                .SetPriority(NotificationCompat.PriorityMin)
                .SetVisibility(NotificationCompat.VisibilityPublic);

            return builder.Build();
        }

        PendingIntent GetActionIntent(string action, int requestCode)
        {
            Intent intent = new Intent(this, typeof(NotificationActionReceiver));
            intent.SetAction(action);
            intent.SetPackage(PackageName);
            return PendingIntent.GetBroadcast(this, requestCode, intent, PendingIntentFlags.Immutable);
        }

        public static void Pause()
        {
            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(
                    PlaybackStateCompat.ActionPlay |
                    PlaybackStateCompat.ActionPause |
                    PlaybackStateCompat.ActionSkipToNext |
                    PlaybackStateCompat.ActionSkipToPrevious
                )
                .SetState(PlaybackStateCompat.StatePaused, PlaybackStateCompat.PlaybackPositionUnknown, 1.0f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            IsPlaying = false;
        }

        public static void Play()
        {
            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(
                    PlaybackStateCompat.ActionPlay |
                    PlaybackStateCompat.ActionPause |
                    PlaybackStateCompat.ActionSkipToNext |
                    PlaybackStateCompat.ActionSkipToPrevious
                )
                .SetState(PlaybackStateCompat.StatePlaying, PlaybackStateCompat.PlaybackPositionUnknown, 1.0f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            IsPlaying = true;
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
    }
}
