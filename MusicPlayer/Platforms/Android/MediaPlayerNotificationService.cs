using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Media;
using Android.OS;
using Android.Support.V4.Media;
using Android.Support.V4.Media.Session;
using Android.Widget;
using AndroidX.Core.App;
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

        MediaSessionCompat mediaSession;

        public override void OnCreate()
        {
            base.OnCreate();
            CreateNotificationChannel();

            mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
            mediaSession.Active = true;
        }

        public override StartCommandResult OnStartCommand(Intent intent, StartCommandFlags flags, int startId)
        {
            var metadata = new MediaMetadataCompat.Builder()
                .PutString(MediaMetadata.MetadataKeyTitle, "Song Title")
                .PutString(MediaMetadata.MetadataKeyArtist, "Artist Name")
                .PutBitmap(MediaMetadata.MetadataKeyAlbumArt, BitmapFactory.DecodeResource(Resources, Resource.Drawable.default_playlist))
                .Build();

            mediaSession.SetMetadata(metadata);

            var playbackState = new PlaybackStateCompat.Builder()
                .SetActions(PlaybackStateCompat.ActionSetShuffleMode |
                            PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious |
                            PlaybackStateCompat.ActionSetRepeatMode)
                .SetState(PlaybackStateCompat.StatePlaying, 0, 1f)
                .Build();

            mediaSession.SetPlaybackState(playbackState);

            var notification = BuildNotification();
            StartForeground(1, notification);
            return StartCommandResult.Sticky;
        }

        Notification BuildNotification()
        {
            Intent intent = new Intent(this, typeof(MainActivity));
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
            PendingIntent pendingIntent = PendingIntent.GetActivity(this, 0, intent, PendingIntentFlags.Immutable);

            var style = new AndroidX.Media.App.NotificationCompat.MediaStyle()
                .SetMediaSession(mediaSession.SessionToken)
                .SetShowActionsInCompactView(1, 2, 3);

            var builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .SetContentTitle("Song Name")
                .SetContentText("Song Artist")
                .SetSmallIcon(Resource.Drawable.small_icon)
                .SetStyle(style)
                .AddAction(new NotificationCompat.Action(Resource.Drawable.shuffle, "Shuffle", GetActionIntent("SHUFFLE", 100)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.previous, "Previous", GetActionIntent("PREVIOUS", 101)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.play, "Play", GetActionIntent("PLAY", 102)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.next, "Next", GetActionIntent("NEXT", 103)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.repeat_album, "Repeat", GetActionIntent("REPEAT", 104)))
                .SetOngoing(true)
                .SetOnlyAlertOnce(true)
                .SetAutoCancel(false)
                .SetContentIntent(pendingIntent)
                .SetPriority(NotificationCompat.PriorityHigh)
                .SetVisibility(NotificationCompat.VisibilityPublic);

            return builder.Build();
        }

        PendingIntent GetActionIntent(string action, int requestCode)
        {
            Intent intent = new Intent(this, typeof(NotificationActionReceiver));
            intent.SetAction(action);
            return PendingIntent.GetBroadcast(this, requestCode, intent, PendingIntentFlags.Immutable);
        }

        void CreateNotificationChannel()
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
            {
                var channel = new NotificationChannel(CHANNEL_ID, "Media Playback", NotificationImportance.Max)
                {
                    Description = "Playback controls",
                    LockscreenVisibility = NotificationVisibility.Public
                };
                channel.SetShowBadge(false);
                var manager = (NotificationManager)GetSystemService(NotificationService);
                manager.CreateNotificationChannel(channel);
            }
        }

        public override IBinder OnBind(Intent intent) => null;
    }
}
