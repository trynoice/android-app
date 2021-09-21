---
layout: page
title: v2 Announcement
---

# v2 Announcement

When we began in 2019, Noice was just under 3 MBs packed with tiny and
low-quality sounds. It has grown a lot since then. Last year, we introduced an
advanced dual-sampled sound engine, an idea offered by a user.[^1] It allowed
Noice to work fully offline while maintaining comparable audio quality and
surpassing performance than its peers.

## It's time to rethink our priorities

Over time, we have gathered ample feedback from our users. While we promptly
acted on the most, some of it requires changes at a basic level. Starting
v2.0.0, we will begin phased releases incorporating these changes.

### State of the art sound engine

The dual-sampled sound engine works well, but it's time for the next best thing.
The operating principle for our peers is looping a 5-30 mins sound indefinitely.
We want to change that by building a sound engine to generate highly dynamic
soundscapes that feel more natural.

### Remote sound library

With growing requirements, we no longer think it best to pack sounds with the
APK. It raises the APK size and hence, doesn't allow for longer or many sounds.
To remove these constraints, we'll start serving the library over the internet.

### Cross-platform service

The cross-platform operation has always been a concern. In the future versions,
we'll begin shipping a web interface along with the hosted library. It will make
Noice available to virtually every device with an Internet browser.

### For creators

Many bad reviews on the Play Store indicate that creators want to export
soundscapes to use them in their videos. We'll be working on facilitating this
on both the software and the audio licensing levels.

### A freemium model to sustain future development

We found the "pay what you want" model unsustainable for Noice. It has barely
generated any revenue in the past year, although Noice has upwards of 6k active
users from Google Play and some more from untrackable sources[^2]. Hence, we've
decided to offer paid subscriptions. Noice will remain open-source, and whatever
was free before will be free forever, but many new features will require an
active subscription.

---

#### Footnotes

[^1]: Essentially, for each sound, we took two small but different samples of
      varying lengths from longer clips, e.g. 40 secs and 45 secs. When played
      in parallel, the listener perceived that the sound was longer (around 5-6
      minutes). Read more about it on the [GitHub
      issue](https://github.com/ashutoshgngwr/noice/issues/62)!

[^2]: Sources like
      [F-Droid](https://f-droid.org/en/packages/com.github.ashutoshgngwr.noice/)
      and [GitHub releases](https://github.com/ashutoshgngwr/noice/releases)
      that don't track user metrics.
