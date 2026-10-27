package fptu.semester8.mss301.propertiesservice.features.listingintegration.api;

import fptu.semester8.mss301.propertiesservice.features.listingintegration.api.dto.ListingRoomSnapshotResponse;
import fptu.semester8.mss301.propertiesservice.features.listingintegration.application.ListingSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/rooms")
public class InternalListingSnapshotController {
    private final ListingSnapshotService service;

    public InternalListingSnapshotController(ListingSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/{roomId}/listing-snapshot")
    public ListingRoomSnapshotResponse getListingSnapshot(@PathVariable long roomId) {
        return service.getRoomSnapshot(roomId);
    }
}
