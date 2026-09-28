package com.dilaw.mohe.notification.dto;

import java.util.List;

/**
 * What a send to the chosen countries could go out on.
 *
 * @param settingsCountryId the country whose settings row applies; null when the global row does
 * @param enabledChannels   the deliverable channels switched on in that row (EMAIL, SMS, WHATSAPP)
 */
public record ChannelAvailabilityDto(Integer settingsCountryId, List<String> enabledChannels) {
}
