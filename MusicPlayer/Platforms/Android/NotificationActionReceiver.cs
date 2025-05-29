using Android.App;
using Android.Content;
using Android.Support.V4.Media.Session;
using Android.Widget;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    [BroadcastReceiver(Enabled = true, Exported = true)]
    [IntentFilter(new[] { "PREVIOUS", "PLAY", "PAUSE", "NEXT" })]
    public class NotificationActionReceiver : BroadcastReceiver
    {
        public override void OnReceive(Context context, Intent intent)
        {
            var action = intent.Action;

            if (action != null )
            {
                switch (action)
                {
                    case "PAUSE":
                        MediaPlayerNotificationService.Pause();
                        break;

                    case "PLAY":
                        MediaPlayerNotificationService.Play();
                        break;
                }
            }

            MessagingCenter.Send<object, string>(this, "MediaAction", action);

            Toast.MakeText(context, $"Action: {action}", ToastLength.Short).Show();
        }
    }
}
