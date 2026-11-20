//! Platform-independent foundation for Mora Audio.
//!
//! This crate deliberately does not claim to route Android's global audio or open a
//! USB device. It models capabilities, exclusive-session ownership, and safe state
//! transitions so the Android/Kernelsu integration can be built and tested separately.

use std::collections::BTreeSet;
use std::fmt;

pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputMode {
    /// Android AudioFlinger / vendor audio policy remains the system mixer.
    SystemWide,
    /// Mora Audio owns one direct USB playback session, when the platform permits it.
    Exclusive,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum PcmFormat {
    Pcm16,
    Pcm24Packed,
    Pcm32,
    Float32,
}

impl PcmFormat {
    pub const fn bits_per_sample(self) -> u8 {
        match self {
            Self::Pcm16 => 16,
            Self::Pcm24Packed => 24,
            Self::Pcm32 | Self::Float32 => 32,
        }
    }

    /// Storage width of one interleaved PCM sample, in bytes.
    pub const fn bytes_per_sample(self) -> u8 {
        match self {
            Self::Pcm16 => 2,
            Self::Pcm24Packed => 3,
            Self::Pcm32 | Self::Float32 => 4,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UsbAudioCapabilities {
    pub sample_rates_hz: BTreeSet<u32>,
    pub formats: BTreeSet<PcmFormat>,
    pub channel_counts: BTreeSet<u8>,
}

impl UsbAudioCapabilities {
    pub fn validate(&self) -> Result<(), CapabilityError> {
        if self.sample_rates_hz.is_empty() {
            return Err(CapabilityError::NoSampleRates);
        }
        if self.sample_rates_hz.contains(&0) {
            return Err(CapabilityError::InvalidSampleRate);
        }
        if self.formats.is_empty() {
            return Err(CapabilityError::NoPcmFormats);
        }
        if self.channel_counts.is_empty()
            || self.channel_counts.iter().any(|channels| *channels == 0)
        {
            return Err(CapabilityError::InvalidChannelCount);
        }
        Ok(())
    }

    pub fn supports(&self, request: &PcmRequest) -> bool {
        self.sample_rates_hz.contains(&request.sample_rate_hz)
            && self.formats.contains(&request.format)
            && self.channel_counts.contains(&request.channels)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PcmRequest {
    pub sample_rate_hz: u32,
    pub format: PcmFormat,
    pub channels: u8,
}

impl PcmRequest {
    /// Validate the stream parameters and return bytes in one interleaved PCM frame.
    pub fn frame_bytes(&self) -> Result<usize, PcmRequestError> {
        if self.sample_rate_hz == 0 {
            return Err(PcmRequestError::InvalidSampleRate);
        }
        if self.channels == 0 {
            return Err(PcmRequestError::InvalidChannelCount);
        }
        Ok(usize::from(self.format.bytes_per_sample()) * usize::from(self.channels))
    }

    /// Return the uncompressed PCM payload rate, excluding USB/protocol overhead.
    pub fn bytes_per_second(&self) -> Result<u64, PcmRequestError> {
        let frame_bytes = u64::try_from(self.frame_bytes()?)
            .map_err(|_| PcmRequestError::ArithmeticOverflow)?;
        u64::from(self.sample_rate_hz)
            .checked_mul(frame_bytes)
            .ok_or(PcmRequestError::ArithmeticOverflow)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PcmRequestError {
    InvalidSampleRate,
    InvalidChannelCount,
    ArithmeticOverflow,
}

impl fmt::Display for PcmRequestError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl std::error::Error for PcmRequestError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CapabilityError {
    NoSampleRates,
    InvalidSampleRate,
    NoPcmFormats,
    InvalidChannelCount,
}

impl fmt::Display for CapabilityError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl std::error::Error for CapabilityError {}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub enum DeviceState {
    #[default]
    Detached,
    Attached { device_id: String },
    Ready { device_id: String, capabilities: UsbAudioCapabilities },
    ExclusiveOwned { device_id: String, request: PcmRequest },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineError {
    EmptyDeviceId,
    DeviceNotReady,
    ExclusiveAlreadyOwned,
    UnsupportedPcmRequest,
    NoExclusiveSession,
}

impl fmt::Display for EngineError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl std::error::Error for EngineError {}

/// A deterministic state machine. Android USB APIs and audio policy calls live in
/// platform adapters, never in this portable core.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct PlaybackCoordinator {
    state: DeviceState,
}

impl PlaybackCoordinator {
    pub fn state(&self) -> &DeviceState {
        &self.state
    }

    pub fn attach(&mut self, device_id: impl Into<String>) -> Result<(), EngineError> {
        let device_id = device_id.into();
        if device_id.trim().is_empty() {
            return Err(EngineError::EmptyDeviceId);
        }
        self.state = DeviceState::Attached { device_id };
        Ok(())
    }

    pub fn mark_ready(
        &mut self,
        capabilities: UsbAudioCapabilities,
    ) -> Result<(), CapabilityError> {
        capabilities.validate()?;
        let device_id = match &self.state {
            DeviceState::Attached { device_id } | DeviceState::Ready { device_id, .. } => {
                device_id.clone()
            }
            DeviceState::ExclusiveOwned { .. } | DeviceState::Detached => {
                return Err(CapabilityError::NoPcmFormats);
            }
        };
        self.state = DeviceState::Ready { device_id, capabilities };
        Ok(())
    }

    pub fn acquire_exclusive(&mut self, request: PcmRequest) -> Result<(), EngineError> {
        let (device_id, capabilities) = match &self.state {
            DeviceState::Ready { device_id, capabilities } => (device_id.clone(), capabilities),
            DeviceState::ExclusiveOwned { .. } => return Err(EngineError::ExclusiveAlreadyOwned),
            DeviceState::Detached | DeviceState::Attached { .. } => {
                return Err(EngineError::DeviceNotReady)
            }
        };
        if !capabilities.supports(&request) {
            return Err(EngineError::UnsupportedPcmRequest);
        }
        self.state = DeviceState::ExclusiveOwned { device_id, request };
        Ok(())
    }

    pub fn release_exclusive(&mut self) -> Result<(), EngineError> {
        let device_id = match &self.state {
            DeviceState::ExclusiveOwned { device_id, .. } => device_id.clone(),
            _ => return Err(EngineError::NoExclusiveSession),
        };
        // The caller must refresh capabilities after a real device/session teardown.
        self.state = DeviceState::Attached { device_id };
        Ok(())
    }

    pub fn detach(&mut self) {
        // Detach always revokes ownership. Platform code must stop/close the USB stream.
        self.state = DeviceState::Detached;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn caps() -> UsbAudioCapabilities {
        UsbAudioCapabilities {
            sample_rates_hz: [44_100, 48_000, 96_000].into_iter().collect(),
            formats: [PcmFormat::Pcm16, PcmFormat::Pcm24Packed].into_iter().collect(),
            channel_counts: [2].into_iter().collect(),
        }
    }

    fn request() -> PcmRequest {
        PcmRequest { sample_rate_hz: 96_000, format: PcmFormat::Pcm24Packed, channels: 2 }
    }

    #[test]
    fn exclusive_session_requires_ready_device_and_supported_format() {
        let mut coordinator = PlaybackCoordinator::default();
        assert_eq!(coordinator.acquire_exclusive(request()), Err(EngineError::DeviceNotReady));
        coordinator.attach("usb:1").unwrap();
        coordinator.mark_ready(caps()).unwrap();
        coordinator.acquire_exclusive(request()).unwrap();
        assert!(matches!(coordinator.state(), DeviceState::ExclusiveOwned { .. }));
    }

    #[test]
    fn unsupported_request_is_rejected_without_changing_state() {
        let mut coordinator = PlaybackCoordinator::default();
        coordinator.attach("usb:1").unwrap();
        coordinator.mark_ready(caps()).unwrap();
        let unsupported = PcmRequest { sample_rate_hz: 192_000, ..request() };
        assert_eq!(
            coordinator.acquire_exclusive(unsupported),
            Err(EngineError::UnsupportedPcmRequest)
        );
        assert!(matches!(coordinator.state(), DeviceState::Ready { .. }));
    }

    #[test]
    fn detach_revokes_exclusive_ownership() {
        let mut coordinator = PlaybackCoordinator::default();
        coordinator.attach("usb:1").unwrap();
        coordinator.mark_ready(caps()).unwrap();
        coordinator.acquire_exclusive(request()).unwrap();
        coordinator.detach();
        assert_eq!(coordinator.state(), &DeviceState::Detached);
    }

    #[test]
    fn invalid_capabilities_are_rejected() {
        let empty = UsbAudioCapabilities {
            sample_rates_hz: BTreeSet::new(),
            formats: [PcmFormat::Pcm16].into_iter().collect(),
            channel_counts: [2].into_iter().collect(),
        };
        assert_eq!(empty.validate(), Err(CapabilityError::NoSampleRates));
    }

    #[test]
    fn pcm_frame_width_uses_packed_24_bit_storage() {
        let request = PcmRequest { sample_rate_hz: 48_000, format: PcmFormat::Pcm24Packed, channels: 2 };
        assert_eq!(request.frame_bytes(), Ok(6));
        assert_eq!(request.bytes_per_second(), Ok(288_000));
    }

    #[test]
    fn pcm_payload_rate_handles_common_formats() {
        let pcm16 = PcmRequest { sample_rate_hz: 44_100, format: PcmFormat::Pcm16, channels: 2 };
        let pcm32 = PcmRequest { sample_rate_hz: 96_000, format: PcmFormat::Float32, channels: 2 };
        assert_eq!(pcm16.bytes_per_second(), Ok(176_400));
        assert_eq!(pcm32.bytes_per_second(), Ok(768_000));
    }

    #[test]
    fn pcm_request_rejects_zero_rate_and_channels() {
        let zero_rate = PcmRequest { sample_rate_hz: 0, ..request() };
        let zero_channels = PcmRequest { channels: 0, ..request() };
        assert_eq!(zero_rate.frame_bytes(), Err(PcmRequestError::InvalidSampleRate));
        assert_eq!(zero_channels.frame_bytes(), Err(PcmRequestError::InvalidChannelCount));
    }

    #[test]
    fn empty_device_identifier_is_rejected() {
        let mut coordinator = PlaybackCoordinator::default();
        assert_eq!(coordinator.attach("  "), Err(EngineError::EmptyDeviceId));
        assert_eq!(coordinator.state(), &DeviceState::Detached);
    }
}
