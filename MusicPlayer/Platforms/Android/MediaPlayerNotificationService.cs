using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.OS;
using AndroidX.Core.App;
using Plugin.LocalNotification;
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

        public override void OnCreate()
        {
            base.OnCreate();
            CreateNotificationChannel();
        }

        public override StartCommandResult OnStartCommand(Intent intent, StartCommandFlags flags, int startId)
        {
            var notification = BuildNotification();
            StartForeground(1, notification);
            return StartCommandResult.Sticky;
        }

        Notification BuildNotification()
        {
            var builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .SetContentTitle("Song Name")
                .SetContentText("Song Artist")
                .SetSmallIcon(Resource.Drawable.small_icon)
                .SetLargeIcon(BitmapFactory.DecodeResource(Resources, Resource.Drawable.large_icon))
                .SetStyle(new AndroidX.Media.App.NotificationCompat.MediaStyle()
                    .SetShowActionsInCompactView(1, 2, 3))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.shuffle, "Shuffle", GetActionIntent("SHUFFLE", 100)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.previous, "Previous", GetActionIntent("PREVIOUS", 101)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.play, "Play", GetActionIntent("PLAY", 102)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.next, "Next", GetActionIntent("NEXT", 103)))
                .AddAction(new NotificationCompat.Action(Resource.Drawable.repeat_album, "Repeat", GetActionIntent("REPEAT", 104)))
                .SetProgress(100, 50, false)
                .SetOngoing(true)
                .SetOnlyAlertOnce(true)
                .SetVisibility(NotificationCompat.VisibilityPublic)
                .SetPriority((int)NotificationPriority.Max);

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
                var channel = new NotificationChannel(CHANNEL_ID, "Media Playback", NotificationImportance.High)
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
