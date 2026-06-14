#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <MediaPlayer/MediaPlayer.h>

typedef void (*nowplaying_simple_cb)(void);
typedef void (*nowplaying_seek_cb)(double position_seconds);
typedef void (*nowplaying_rate_cb)(double rate);

static nowplaying_simple_cb _cb_play     = NULL;
static nowplaying_simple_cb _cb_pause    = NULL;
static nowplaying_simple_cb _cb_toggle   = NULL;
static nowplaying_simple_cb _cb_stop     = NULL;
static nowplaying_simple_cb _cb_next     = NULL;
static nowplaying_simple_cb _cb_previous = NULL;
static nowplaying_seek_cb   _cb_seek     = NULL;
static nowplaying_rate_cb   _cb_rate     = NULL;

void* nowplaying_default_center(void) {
    return (__bridge_retained void *)[MPNowPlayingInfoCenter defaultCenter];
}

void nowplaying_register_commands_with_callbacks(
    nowplaying_simple_cb on_play,
    nowplaying_simple_cb on_pause,
    nowplaying_simple_cb on_toggle,
    nowplaying_simple_cb on_stop,
    nowplaying_simple_cb on_next,
    nowplaying_simple_cb on_previous,
    nowplaying_seek_cb   on_seek,
    nowplaying_rate_cb   on_rate)
{
    _cb_play     = on_play;
    _cb_pause    = on_pause;
    _cb_toggle   = on_toggle;
    _cb_stop     = on_stop;
    _cb_next     = on_next;
    _cb_previous = on_previous;
    _cb_seek     = on_seek;
    _cb_rate     = on_rate;

    MPRemoteCommandCenter *rcc = [MPRemoteCommandCenter sharedCommandCenter];

    rcc.playCommand.enabled                   = YES;
    rcc.pauseCommand.enabled                  = YES;
    rcc.togglePlayPauseCommand.enabled        = YES;
    rcc.stopCommand.enabled                   = YES;
    rcc.nextTrackCommand.enabled              = YES;
    rcc.previousTrackCommand.enabled          = YES;
    rcc.changePlaybackPositionCommand.enabled = YES;
    rcc.changePlaybackRateCommand.enabled     = (on_rate != NULL);

    [rcc.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_play) _cb_play();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_pause) _cb_pause();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_toggle) _cb_toggle();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.stopCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_stop) _cb_stop();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_next) _cb_next();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_previous) _cb_previous();
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.changePlaybackPositionCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_seek) {
            MPChangePlaybackPositionCommandEvent *pe = (MPChangePlaybackPositionCommandEvent *)e;
            _cb_seek(pe.positionTime);
        }
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [rcc.changePlaybackRateCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (_cb_rate) {
            MPChangePlaybackRateCommandEvent *re = (MPChangePlaybackRateCommandEvent *)e;
            _cb_rate(re.playbackRate);
        }
        return MPRemoteCommandHandlerStatusSuccess;
    }];
}

// state: 1=playing, 2=paused, 3=stopped  (MPNowPlayingPlaybackState)
void nowplaying_set_playback_state(void *center, int state) {
    MPNowPlayingInfoCenter *c = (__bridge MPNowPlayingInfoCenter *)center;
    c.playbackState = (MPNowPlayingPlaybackState)state;
}

// Publish all metadata + playback position. Pass NULL/0 for optional fields to leave them unchanged.
void nowplaying_push_info(void *center,
                          const char *title,
                          const char *artist,
                          const char *album,
                          const char *album_artist,
                          const char *genre,
                          double elapsed,
                          double total,
                          double rate,
                          int track_number) {
    MPNowPlayingInfoCenter *c = (__bridge MPNowPlayingInfoCenter *)center;
    NSMutableDictionary *info = [c.nowPlayingInfo mutableCopy] ?: [NSMutableDictionary dictionary];

    if (title)            info[MPMediaItemPropertyTitle]                    = @(title);
    if (artist)           info[MPMediaItemPropertyArtist]                   = @(artist);
    if (album)            info[MPMediaItemPropertyAlbumTitle]               = @(album);
    if (album_artist)     info[MPMediaItemPropertyAlbumArtist]              = @(album_artist);
    if (genre)            info[MPMediaItemPropertyGenre]                    = @(genre);
    if (total > 0)        info[MPMediaItemPropertyPlaybackDuration]         = @(total);
    if (elapsed >= 0)     info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(elapsed);
    if (rate > 0)         info[MPNowPlayingInfoPropertyPlaybackRate]        = @(rate);
    if (track_number > 0) info[MPMediaItemPropertyAlbumTrackNumber]        = @(track_number);
    info[MPNowPlayingInfoPropertyMediaType] = @(MPNowPlayingInfoMediaTypeAudio);

    c.nowPlayingInfo = info;
}


// Downloads the image at url_str asynchronously and sets it as the artwork.
// Operates directly on the defaultCenter singleton to avoid cross-thread pointer passing.
void nowplaying_set_artwork_url(const char *url_str) {
    if (!url_str || strlen(url_str) == 0) return;
    NSString *str = [NSString stringWithUTF8String:url_str];
    // Use fileURLWithPath for bare paths (no scheme); URLWithString for http/https/file://
    NSURL *url = ([str hasPrefix:@"/"] || [str hasPrefix:@"~"])
        ? [NSURL fileURLWithPath:str]
        : [NSURL URLWithString:str];
    if (!url) return;

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSData *data = [NSData dataWithContentsOfURL:url];
        if (!data) return;
        NSImage *image = [[NSImage alloc] initWithData:data];
        if (!image) return;
        MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc]
            initWithBoundsSize:image.size
                requestHandler:^NSImage *(CGSize size) { return image; }];
        dispatch_async(dispatch_get_main_queue(), ^{
            MPNowPlayingInfoCenter *c = [MPNowPlayingInfoCenter defaultCenter];
            NSMutableDictionary *info = [c.nowPlayingInfo mutableCopy] ?: [NSMutableDictionary dictionary];
            info[MPMediaItemPropertyArtwork] = artwork;
            c.nowPlayingInfo = info;
        });
    });
}

void nowplaying_clear(void *center) {
    MPNowPlayingInfoCenter *c = (__bridge MPNowPlayingInfoCenter *)center;
    c.nowPlayingInfo = nil;
}

void nowplaying_release_center(void *center) {
    id __attribute__((unused)) obj = (__bridge_transfer id)center;
}
