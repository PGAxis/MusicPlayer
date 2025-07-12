using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Media;
using Android.Media.Session;
using Android.Net;
using Android.OS;
using Android.Support.V4.Media.Session;
using AndroidX.Media3.Common;
using AndroidX.Media3.ExoPlayer;
using AndroidX.Media3.Session;
using AndroidX.Media3.UI;
using Google.Common.Util.Concurrent;
using Java.Lang;
using MediaSession = AndroidX.Media3.Session.MediaSession;
using Uri = Android.Net.Uri;

namespace MusicPlayer
{
    [Service(Name = "com.pg_axis.musicplayer.MusicPlaybackService", Exported = true,
        ForegroundServiceType = Android.Content.PM.ForegroundService.TypeMediaPlayback)]
    public class MusicPlaybackService : MediaSessionService
    {
        private MediaSession mediaSession;
        private Android.Media.Session.MediaSession mediaSessionC;
        private IExoPlayer player;

        private Settings settings = Settings.Instance();

        public override void OnCreate()
        {
            base.OnCreate();

            var context = this;
            IExoPlayer? player = new ExoPlayerBuilder(context).Build();

            var sessionCallback = new MediaSessionCallback();

            mediaSession = new MediaSession.Builder(context, player)
                .SetCallback(sessionCallback)
                .SetId("MusicPlayerSession")
                .Build();

            mediaSessionC = new Android.Media.Session.MediaSession(context, "MediaSessionTag");
            mediaSessionC.Active = true;

            var metadata = new Android.Media.MediaMetadata.Builder()
                .PutString(Android.Media.MediaMetadata.MetadataKeyTitle, settings.LastSongName)
                .PutString(Android.Media.MediaMetadata.MetadataKeyArtist, settings.LastSongArtist)
                .PutBitmap(Android.Media.MediaMetadata.MetadataKeyAlbumArt, BitmapFactory.DecodeFile(settings.LastSongCoverPath))
                .Build();

            mediaSessionC.SetMetadata(metadata);

            var playbackState = new PlaybackState.Builder()
                .SetActions(PlaybackStateCompat.ActionSetShuffleMode |
                            PlaybackStateCompat.ActionSetRepeatMode |
                            PlaybackStateCompat.ActionPlay |
                            PlaybackStateCompat.ActionPause |
                            PlaybackStateCompat.ActionSkipToNext |
                            PlaybackStateCompat.ActionSkipToPrevious)
                .SetState(PlaybackStateCode.Playing, settings.LastSongTime, 1f)
                .Build();

            mediaSessionC.SetPlaybackState(playbackState);

            var mediaItem = new MediaItem.Builder()
                .SetUri(Uri.Parse("file:///sdcard/Music/attention.mp3"))
                .SetMediaMetadata(new AndroidX.Media3.Common.MediaMetadata.Builder()
                    .SetTitle("Attention")
                    .SetArtist("Charlie Puth")
                    .SetArtworkUri(Uri.Parse("file:///sdcard/Music/cover.jpg"))
                    .Build())
                .Build();

            var mediaDescriptionAdapter = new MediaDescriptionAdapter();
            var notificationManager = new PlayerNotificationManager.Builder(this, 1, "music_channel")
                .SetMediaDescriptionAdapter(mediaDescriptionAdapter)
                .SetSmallIconResourceId(Resource.Drawable.small_icon)
                .SetChannelImportance((int)NotificationImportance.Min)
                .Build();

            notificationManager.SetPlayer(player);

            notificationManager.SetUseNextAction(true);
            notificationManager.SetUsePreviousAction(true);
            notificationManager.SetUseFastForwardAction(true);
            notificationManager.SetUseRewindAction(true);
            notificationManager.SetUsePlayPauseActions(true);

            notificationManager.SetMediaSessionToken(mediaSessionC.SessionToken);

            player.SetMediaItem(mediaItem);
            player.Prepare();
        }

        public override void OnDestroy()
        {
            mediaSession.Release();
            player.Release();
            base.OnDestroy();
        }

        public override MediaSession? OnGetSession(MediaSession.ControllerInfo? p0)
        {
            return mediaSession;
        }

        class MediaDescriptionAdapter : Java.Lang.Object, PlayerNotificationManager.IMediaDescriptionAdapter
        {
            public PendingIntent? CreateCurrentContentIntent(IPlayer? player)
            {
                Intent intent = new Intent(Android.App.Application.Context, typeof(MainActivity));
                intent.SetFlags(ActivityFlags.SingleTop);
                return PendingIntent.GetActivity(Android.App.Application.Context, 0, intent, PendingIntentFlags.Immutable);
            }

            public ICharSequence? GetCurrentContentTextFormatted(IPlayer? player)
            {
                return player.CurrentMediaItem.MediaMetadata.Title;
            }

            public ICharSequence? GetCurrentContentTitleFormatted(IPlayer? player)
            {
                return player.CurrentMediaItem.MediaMetadata.Artist;
            }

            public Bitmap? GetCurrentLargeIcon(IPlayer? player, PlayerNotificationManager.BitmapCallback? callback)
            {
                throw new NotImplementedException();
            }
        }

        class MediaSessionCallback : Java.Lang.Object, MediaSession.ICallback
        {
            public bool OnPlay(MediaSession session, MediaSession.ControllerInfo controller)
            {
                session.Player.Play();
                return true;
            }

            public bool OnPause(MediaSession session, MediaSession.ControllerInfo controller)
            {
                session.Player.Pause();
                return true;
            }

            public bool OnSeekTo(MediaSession session, MediaSession.ControllerInfo controller, long positionMs)
            {
                session.Player.SeekTo(positionMs);
                return true;
            }

            public bool OnSkipToNext(MediaSession session, MediaSession.ControllerInfo controller)
            {
                session.Player.SeekToNext();
                return true;
            }

            public bool OnSkipToPrevious(MediaSession session, MediaSession.ControllerInfo controller)
            {
                session.Player.SeekToPrevious();
                return true;
            }
        }
    }
}
