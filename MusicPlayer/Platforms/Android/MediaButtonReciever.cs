using Android.App;
using Android.Content;
using Android.Support.V4.Media.Session;
using AndroidX.Media.Session;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    [BroadcastReceiver(Enabled = true, Exported = true)]
    [IntentFilter(new[] { Intent.ActionMediaButton })]
    public class MediaButtonReciever : BroadcastReceiver
    {
        public override void OnReceive(Context context, Intent intent)
        {
            MediaSessionCompat mediaSession = MediaPlayerNotificationService.mediaSession;

            if (mediaSession != null)
                MediaButtonReceiver.HandleIntent(mediaSession, intent);
        }
    }
}
