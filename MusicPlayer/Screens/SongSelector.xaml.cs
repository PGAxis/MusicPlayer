using System.Collections.ObjectModel;
using System.ComponentModel;

namespace MusicPlayer;

public partial class SongSelector : ContentPage
{
    public ObservableRangeCollection<Song> AllSongs { get; set; } = new();
    public ObservableRangeCollection<Song> FilteredSongs { get; set; } = new();

    public SongSelector()
	{
		InitializeComponent();
        MainStack.HeightRequest = DeviceDisplay.MainDisplayInfo.Height / DeviceDisplay.MainDisplayInfo.Density;
        SetHeight();
        BindingContext = this;
    }

    protected override void OnSizeAllocated(double width, double height)
    {
        base.OnSizeAllocated(width, height);
        LoadSongs();
    }

    private async void LoadSongs()
    {
        var songs = await App.SongDatabase.GetSongsAsync();

        SongCountLabel.Text = songs.Count.ToString();

        AllSongs.ReplaceRange(songs);

        FilterSongs(null);
    }

    private void FilterSongs(string? query)
    {
        if (query == null)
        {
            FilteredSongs.ReplaceRange(AllSongs);
        }
        else
        {
            query = query.ToLower();
            var filteredList = AllSongs.AsParallel().Where(song => song.Title.Contains(query, StringComparison.OrdinalIgnoreCase) || song.Artist.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();

            FilteredSongs.ReplaceRange(filteredList);
        }

    }

    private void SearchBar_TextChanged(object sender, TextChangedEventArgs e)
    {
        FilterSongs(e.NewTextValue);
    }

    private void SongsCollection_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        // You can handle multi-selection logic here
    }

    private void OnAddToPlaylistClicked(object sender, EventArgs e)
    {
        if (sender is SwipeItem swipeItem && swipeItem.BindingContext is Song song)
        {
            DisplayAlert("Add to Playlist", $"Add {song.Title} to a playlist.", "OK");
        }
    }

    private void OnContextMenuClicked(object sender, EventArgs e)
    {
        if (sender is ImageButton button && button.BindingContext is Song song)
        {
            // Future context menu or action sheet logic here
            DisplayActionSheet("Song Options", "Cancel", null, "Add to Playlist");
        }
    }

    private void ChangeSelection(object sender, CheckedChangedEventArgs e)
    {
        if (SongsCollection.SelectedItems == null)
            return;

        if (SellectAllButton.IsChecked)
        {
            SongsCollection.SelectedItems.Clear();
            foreach (var song in FilteredSongs)
                SongsCollection.SelectedItems.Add(song);
        }
        else
        {
            SongsCollection.SelectedItems.Clear();
        }
    }

    private void SetHeight()
    {
        double paddingTop = MainStack.Padding.Top;
        double paddingBottom = MainStack.Padding.Bottom;

        double searchBarHeight = searchBar.Height;
        double nameStackHeight = NameStack.Height;

        double spacing = 10;
        double marginBottom = 10;

        double totalUsedHeight = paddingTop + searchBarHeight + spacing + nameStackHeight + marginBottom + paddingBottom;
        double screenHeight = DeviceDisplay.MainDisplayInfo.Height / DeviceDisplay.MainDisplayInfo.Density;

        double remainingHeight = screenHeight - totalUsedHeight;
        SongsCollection.HeightRequest = remainingHeight;
    }
}