package com.trynoice.api.client.models

import java.util.*

/**
 * An entity describing the sound library.
 *
 * @property segmentsBasePath The path relative to the library manifest where individual segment
 * clips are accessible at `${segmentsBasePath}/${sound.id}/${segment.name}.m3u8`.
 * @property groups A list of groups for categorising sounds.
 * @property tags A list of tags for declaring keywords related to sounds.
 * @property sounds A list of available sounds in the library.
 * @property updatedAt timestamp at which the sound library was last updated.
 */
data class LibraryManifest(
  val segmentsBasePath: String,
  val groups: List<SoundGroup>,
  val tags: List<SoundTag>,
  val sounds: List<Sound>,
  val updatedAt: Date,
)

/**
 * An entity describing a sound category.
 *
 * @property id A unique stable snake-cased identifier for a group.
 * @property name A user-presentable name for this group.
 */
data class SoundGroup(val id: String, val name: String)

/**
 * An entity describing a keyword for sounds.
 *
 * @property id A unique stable snake-cased identifier for a tag.
 * @property name A user-presentable name for this tag.
 */
data class SoundTag(val id: String, val name: String)

/**
 * An entity describing various attributes of a sound.
 *
 * @property id A unique stable snake-cased identifier for a sound.
 * @property groupId ID of an existing [SoundGroup] to which this sound belongs.
 * @property name A user-presentable name for this sound.
 * @property icon A user-presentable SVG icon encoded as a data URI.
 * @property maxSilence The upper limit (in seconds) for the amount of silence to add in-between
 * segments for non-contiguous sounds. Clients should randomly choose the length of silence in this
 * range to add after each segment. Moreover, sounds are considered as contiguous if `maxSilence` is
 * set to 0.
 * @property segments A list of segments for this sound.
 * @property tags IDs of existing [SoundTag]s that associate with this sound.
 * @property sources A list of details attributing original clip sources, author and license.
 */
data class Sound(
  val id: String,
  val groupId: String,
  val name: String,
  val icon: String,
  val maxSilence: Int,
  val segments: List<SoundSegment>,
  val tags: List<String>,
  val sources: List<SoundSource>,
)

/**
 * An entity describing a segment of a [Sound].
 *
 * @property name Used for find segments in `${segmentsBasePath}/${soundKey}/${name}.m3u8` path. If
 * the sound is non-contiguous, its bridge segments are found by appending source segment's name to
 * destination segment's name, e.g. `raindrops_light_raindrops_heavy.m3u8`.
 *
 * @property isFree A hint whether a segment is available to unsubscribed users. If a user attempts
 * to access resources despite this hint being `false`, the CDN server returns HTTP 403.
 */
data class SoundSegment(val name: String, val isFree: Boolean)

/**
 * An entity describing an original source of a [Sound]'s asset.
 *
 * @property name The name of the source asset.
 * @property url URL of the source asset.
 * @property license A SPDX license code for the source clip.
 * @property author author of the source asset.
 */
data class SoundSource(
  val name: String,
  val url: String,
  val license: String,
  val author: SoundSourceAuthor? = null,
)

/**
 * @property name Name of the author.
 * @property url URL of the author.
 */
data class SoundSourceAuthor(
  val name: String,
  val url: String,
)
