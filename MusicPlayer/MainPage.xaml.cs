﻿#if ANDROID
using Android.Content;
using Android.Media;
using Android.Provider;
#endif
using System.Linq;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public partial class MainPage : ContentPage
    {
        private bool CanScroll = true;
        public bool isScrollingPaused = false;
        CancellationTokenSource scrollCts;
        private Dictionary<Label, ContentView> Tabs = new Dictionary<Label, ContentView>();
        private List<Label> JustLabelsTabs = new List<Label>();
        public Label LastTab;
        private List<IResizablePage> Pages = new List<IResizablePage>();

        private Favourites Favourites = Favourites.Instance();
        private Playlists Playlists = Playlists.Instance();
        private Songs Songs = Songs.Instance();
        private Albums Albums = Albums.Instance();
        private Interprets Interprets = Interprets.Instance();

        private Settings settings = Settings.Instance();

        protected override void OnSizeAllocated(double width, double height)
        {
            base.OnSizeAllocated(width, height);

            _ = MainThread.InvokeOnMainThreadAsync(async () => await ScrollToTab(LastTab));
        }

        public MainPage()
        {
            InitializeComponent();
            SetCorrectWidthRequest();
            StartScrollingTitle();

            settings.LoadSettings();
            settings.Source = "play.png";

            PlayPauseButton.BindingContext = settings;

            JustLabelsTabs.Add(FavLabel);
            JustLabelsTabs.Add(PlayLabel);
            JustLabelsTabs.Add(SongLabel);
            JustLabelsTabs.Add(AlbLabel);
            JustLabelsTabs.Add(IntLabel);

            Tabs.Add(FavLabel, FavouritesView);
            Tabs.Add(PlayLabel, PlaylistsView);
            Tabs.Add(SongLabel, SongsView);
            Tabs.Add(AlbLabel, AlbumsView);
            Tabs.Add(IntLabel, InterpretsView);

            Pages.Add(Favourites);
            Pages.Add(Playlists);
            Pages.Add(Songs);
            Pages.Add(Albums);
            Pages.Add(Interprets);

            LastTab = JustLabelsTabs[settings.LastTabIndex];
            LastTab.FontAttributes = FontAttributes.Bold;
            if (LastTab == PlayLabel)
            {
                PlaylistAdder.Opacity = 1;
                PlaylistAdder.InputTransparent = false;
            }
            else
            {
                PlaylistAdder.Opacity = 0;
                PlaylistAdder.InputTransparent = true;
            }

            SetCorrectFontSize();

            FavouritesView.Content = Favourites.Content;
            PlaylistsView.Content = Playlists.Content;
            SongsView.Content = Songs.Content;
            AlbumsView.Content = Albums.Content;
            InterpretsView.Content = Interprets.Content;
        }

        private void UserScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double ViewScrollX = curScrollX * 2;

            double screenCenter = TabScroll.Width / 2;

            foreach (var child in LabelStack.Children)
            {
                if (child is Label label)
                {
                    double labelCenter = label.X + (label.Width / 2) - TabScroll.ScrollX;
                    double distance = Math.Abs(screenCenter - labelCenter);

                    double maxFont = 30;
                    double minFont = 20;
                    double maxDistance = TabScroll.Width / 2;

                    double factor = 1 - Math.Min(distance / maxDistance, 1);
                    label.FontSize = minFont + (maxFont - minFont) * factor;
                }
            }
            if (CanScroll)
            {
                _ = CheckScrollChange(curScrollX, TabScroll);
            }
        }

        private void ViewScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double TabScrollX = curScrollX * 0.5;
            if (CanScroll)
            {
                _ = TabScroll.ScrollToAsync(TabScrollX, 0, false);

                _ = CheckScrollChange(curScrollX, ViewScroll);
            }
        }

        //Help methods
        private void SetCorrectWidthRequest()
        {
            double ViewHeightRequest = (DeviceDisplay.MainDisplayInfo.Height / DeviceDisplay.MainDisplayInfo.Density) - 160;
            double ViewRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
            double LabelRequest = ViewRequest * 0.5;
            double PaddingRequest = (ViewRequest - LabelRequest) / 2;
            double SongMetadataRequest = ViewRequest - 235;

            LeftPadding.WidthRequest = PaddingRequest;
            RightPadding.WidthRequest = PaddingRequest;

            FavLabel.WidthRequest = LabelRequest;
            PlayLabel.WidthRequest = LabelRequest;
            SongLabel.WidthRequest = LabelRequest;
            AlbLabel.WidthRequest = LabelRequest;
            IntLabel.WidthRequest = LabelRequest;

            SongMetadataStack.WidthRequest = SongMetadataRequest;

            foreach (var child in ViewStack.Children)
            {
                if (child is ContentView contentView)
                {
                    contentView.WidthRequest = ViewRequest;
                    contentView.HeightRequest = ViewHeightRequest;
                }
            }
        }

        private async Task CheckScrollChange(double scrollX, ScrollView sender)
        {
            await Task.Delay(100);

            double curX = sender.ScrollX;

            bool hasntChanged = AreCloseEnough(curX, scrollX);

            if (hasntChanged && CanScroll)
            {
                Label closest = GetClosestLabel();
                CanScroll = false;
                await TabScroll.ScrollToAsync(closest, ScrollToPosition.Center, false);
                await ViewScroll.ScrollToAsync(Tabs[closest], ScrollToPosition.Center, false);
                settings.SaveSettings(Convert.ToByte(JustLabelsTabs.IndexOf(closest)));
                LastTab.FontAttributes = FontAttributes.None;
                LastTab = closest;
                if (LastTab == PlayLabel)
                {
                    PlaylistAdder.Opacity = 1;
                    PlaylistAdder.InputTransparent = false;
                }
                else
                {
                    PlaylistAdder.Opacity = 0;
                    PlaylistAdder.InputTransparent = true;
                }
                LastTab.FontAttributes = FontAttributes.Bold;
                CanScroll = true;
            }
        }

        private async Task ScrollToTab(Label tab)
        {
            CanScroll = false;
            if (tab == PlayLabel)
            {
                PlaylistAdder.Opacity = 1;
                PlaylistAdder.InputTransparent = false;
            }
            else
            {
                PlaylistAdder.Opacity = 0;
                PlaylistAdder.InputTransparent = true;
            }
            await TabScroll.ScrollToAsync(tab, ScrollToPosition.Center, false);
            await ViewScroll.ScrollToAsync(Tabs[tab], ScrollToPosition.Center, false);
            CanScroll = true;
        }

        private bool AreCloseEnough(double a, double b)
        {
            return Math.Abs(a - b) == 0.0;
        }

        private Label GetClosestLabel()
        {
            double scrollX = TabScroll.ScrollX;
            double scrollWidth = TabScroll.Width;
            double viewportCenter = scrollX + scrollWidth / 2;

            HorizontalStackLayout stack = LabelStack;

            Label closest = null;
            double smallestDistance = double.MaxValue;

            double runningX = 0;

            foreach (var child in stack.Children)
            {
                double childCenter = runningX + child.Width / 2;

                double distance = Math.Abs(viewportCenter - childCenter);
                if (distance < smallestDistance)
                {
                    if (child is Label label)
                    {
                        smallestDistance = distance;
                        closest = (Label)child;
                    }
                }

                runningX += child.Width;
            }

            return closest;
        }

        private void SetCorrectFontSize()
        {
            foreach (var child in LabelStack.Children)
            {
                if (child is Label label)
                {
                    if (child != LastTab)
                    {
                        label.FontSize = 20;
                    }
                }
            }
        }

        public async void StartScrollingTitle()
        {
            await Task.Delay(300);

            double labelWidth = SongTitle.Width;
            double scrollViewWidth = SongMetadataStack.Width;

            if (labelWidth <= scrollViewWidth)
                return;

            SongTitle.Text += "      ";
            SongTitle.Text += SongTitle.Text;

            scrollCts = new CancellationTokenSource();

            try
            {
                while (!scrollCts.IsCancellationRequested)
                {
                    if (!isScrollingPaused)
                    {
                        await SlowScrollToAsync(TitleScrollView, SongTitle.Width / 2);
                        await Task.Delay(750, scrollCts.Token);
                        await TitleScrollView.ScrollToAsync(0, 0, false);
                        await Task.Delay(750, scrollCts.Token);
                    }
                    else
                    {
                        await Task.Delay(500);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
            }
        }

        private async Task SlowScrollToAsync(ScrollView scrollView, double scrollToDistance)
        {
            double x = 0;

            while (x < scrollToDistance)
            {
                x += 0.25;
                if (x > scrollToDistance)
                {
                    x = scrollToDistance;
                }
                await scrollView.ScrollToAsync(x, 0, false);
                await Task.Delay(1);
            }
        }

        private void NotificationPls(object sender, EventArgs e)
        {
#if ANDROID
            var context = Android.App.Application.Context;
            var intent = new Intent(context, typeof(MediaPlayerNotificationService));
            context.StartForegroundService(intent);
#endif
        }

#if ANDROID
        public static async Task<List<Song>> ScanAudioFilesAsync(Context context)
        {
            List<Song> songs = new List<Song>();

            List<Song> existingSongs = await App.SongDatabase.GetSongsAsync();
            HashSet<string> existingPaths = existingSongs.Select(s => s.Path).ToHashSet();

            var uri = MediaStore.Audio.Media.ExternalContentUri;
            string[] projection =
            {
                MediaStore.Audio.Media.InterfaceConsts.Title,
                MediaStore.Audio.Media.InterfaceConsts.Artist,
                MediaStore.Audio.Media.InterfaceConsts.Album,
                MediaStore.Audio.Media.InterfaceConsts.Data,
                MediaStore.Audio.Media.InterfaceConsts.Duration
            };

            using var cursor = context.ContentResolver.Query(uri, projection, null, null, null);

            if (cursor != null && cursor.MoveToFirst())
            {
                int titleIndex = cursor.GetColumnIndex(MediaStore.Audio.Media.InterfaceConsts.Title);
                int artistIndex = cursor.GetColumnIndex(MediaStore.Audio.Media.InterfaceConsts.Artist);
                int albumNameIndex = cursor.GetColumnIndex(MediaStore.Audio.Media.InterfaceConsts.Album);
                int pathIndex = cursor.GetColumnIndex(MediaStore.Audio.Media.InterfaceConsts.Data);
                int durationIndex = cursor.GetColumnIndex(MediaStore.Audio.Media.InterfaceConsts.Duration);

                var tempList = new List<(string title, string artist, string album, string path, double duration, bool isNew)>();

                do
                {
                    string title = cursor.GetString(titleIndex) ?? "Unknown";
                    string artist = cursor.GetString(artistIndex) ?? "Unknown";
                    string album = cursor.GetString(albumNameIndex) ?? "Unknown";
                    string path = cursor.GetString(pathIndex);
                    double durationSec = cursor.GetLong(durationIndex) / 1000.0;

                    if (File.Exists(path))
                    {
                        bool isNew = !existingPaths.Contains(path);
                        tempList.Add((title, artist, album, path, durationSec, isNew));
                        //songs.Add(new Song(title, artist, album, albumArtPath, path, durationSec));
                    }
                }
                while (cursor.MoveToNext());

                var tasks = tempList.Select(songData => Task.Run(() =>
                {
                    string albumArtPath = songData.isNew
                        ? ExtractAlbumArtFromFile(context, songData.path)
                        : existingSongs.FirstOrDefault(s => s.Path == songData.path)?.AlbumArt ?? "default_cover.png";

                    return new Song(songData.title, songData.artist, songData.album, albumArtPath, songData.path, songData.duration);
                })).ToList();

                var completedSongs = await Task.WhenAll(tasks);
                songs.AddRange(completedSongs);

                cursor.Close();
            }

            return songs;
        }

        public static string ExtractAlbumArtFromFile(Context context, string audioFilePath)
        {
            try
            {
                var mmr = new MediaMetadataRetriever();
                mmr.SetDataSource(audioFilePath);

                byte[]? artBytes = mmr.GetEmbeddedPicture();
                if (artBytes != null)
                {
                    string cacheDir = context.CacheDir.AbsolutePath;
                    string fileName = $"{Path.GetFileNameWithoutExtension(audioFilePath)}_cover.jpg";
                    string fullPath = Path.Combine(cacheDir, fileName);
                    if (!File.Exists(fullPath))
                    {
                        File.WriteAllBytes(fullPath, artBytes);
                    }

                    return fullPath;
                }
                else
                {
                    return "default_cover.png";
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
            }

            return "default_cover.png";
        }

        public static async Task SyncSongsAsync()
        {
            var context = Android.App.Application.Context;

            var scannedSongs = await ScanAudioFilesAsync(context);
            var existingSongs = await App.SongDatabase.GetSongsAsync();

            var scannedPaths = scannedSongs.ToDictionary(s => s.Path);
            var existingPaths = existingSongs.ToDictionary(s => s.Path);

            foreach (var song in scannedSongs)
            {
                if (!existingPaths.ContainsKey(song.Path))
                    await App.SongDatabase.SaveSongAsync(song);
            }

            foreach (var song in existingSongs)
            {
                if (!scannedPaths.ContainsKey(song.Path))
                    await App.SongDatabase.DeleteSongAsync(song);
            }
        }

#endif
        private async void SearchSongs(object sender, EventArgs e)
        {
            await Shell.Current.GoToAsync(nameof(SongSelector));
        }

        private async void PlaySong(object sender, EventArgs e)
        {
#if ANDROID
            if (MediaPlayerNotificationService.IsPlaying == false)
            {
                Song song = await App.SongDatabase.GetSongByIdAsync(357);

                settings.LastSongPath = song.Path;
                settings.LastSongName = song.Title;
                settings.LastSongArtist = song.Artist;
                settings.LastSongCoverPath = song.AlbumArt;

                settings.SaveSettings();

                SongTitle.Text = song.Title;
                SongArtist.Text = song.Artist;
                SongCoverImage.Source = song.AlbumArt;

                MediaPlayerNotificationService.StartPlayback(settings.LastSongPath);

                var context = Android.App.Application.Context;
                var intent = new Intent(context, typeof(MediaPlayerNotificationService));
                context.StartForegroundService(intent);                

                settings.Source = "pause.png";
            }
            else
            {
                MediaPlayerNotificationService.Pause();
                settings.Source = "play.png";
            }
#endif
        }

        private void AddPlaylist(object sender, EventArgs e)
        {

        }
    }
}
