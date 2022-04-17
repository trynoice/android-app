package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * An entity describing the sound library.
 *
 * @param segmentsBasePath The path relative to the library manifest where individual segment clips
 * are accessible at `${segmentsBasePath}/${sound.id}/${segment.name}.m3u8`.
 * @param groups A list of groups for categorising sounds.
 * @param sounds A list of available sounds in the library.
 */
data class LibraryManifest(

  @Expose
  val segmentsBasePath: String,

  @Expose
  val groups: List<SoundGroup>,

  @Expose
  val sounds: List<Sound>,
)

/**
 * An entity describing a sound category.
 *
 * @param id A unique stable snake-cased identifier for a group.
 * @param name A user-presentable name for this group.
 */
data class SoundGroup(

  @Expose
  val id: String,

  @Expose
  val name: String,
)

/**
 * An entity describing various attributes of a sound.
 *
 * @param id A unique stable snake-cased identifier for a sound.
 * @param groupId ID of an existing [SoundGroup] to which this sound belongs.
 * @param name A user-presentable name for this sound.
 * @param icon A user-presentable SVG icon encoded as a data URI.
 * @param maxSilence The upper limit (in seconds) for the amount of silence to add in-between
 * segments for non-contiguous sounds. Clients should randomly choose the length of silence in this
 * range to add after each segment. Moreover, sounds are considered as contiguous if `maxSilence` is
 * set to 0.
 * @param segments A list of segments for this sound.
 * @param sources A list of details attributing original clip sources, author and license.
 */
data class Sound(

  @Expose
  val id: String,

  @Expose
  val groupId: String,

  @Expose
  val name: String,

  @Expose
  val icon: String,

  @Expose
  val maxSilence: Int,

  @Expose
  val segments: List<SoundSegment>,

  @Expose
  val sources: List<SoundSource>,
)

/**
 * An entity describing a segment of a [Sound].
 *
 * @param name Used for find segments in `${segmentsBasePath}/${soundKey}/${name}.m3u8` path. If the
 * sound is non-contiguous, its bridge segments are found by appending source segment's name to
 * destination segment's name, e.g. `raindrops_light_raindrops_heavy.m3u8`.
 * @param isFree A hint whether a segment is available to unsubscribed users. If a user attempts to
 * access resources despite this hint being `false`, the CDN server returns HTTP 403.
 */
data class SoundSegment(

  @Expose
  val name: String,

  @Expose
  val isFree: Boolean,
)

/**
 * An entity describing an original source of a [Sound]'s asset.
 *
 * @param name The name of the source asset.
 * @param url URL of the source asset.
 * @param license A SPDX license code for the source clip.
 * @param author author of the source asset.
 */
data class SoundSource(

  @Expose
  val name: String,

  @Expose
  val url: String,

  @Expose
  val license: String,

  @Expose
  val author: SoundSourceAuthor? = null,
)

/**
 * @param name Name of the author.
 * @param url URL of the author.
 */
data class SoundSourceAuthor(

  @Expose
  val name: String,

  @Expose
  val url: String,
)
