package hu.reptrack.web;

import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.PersonalRecordService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** A felhasználó egyéni csúcsai időrendben, a legújabb elöl (UC11). */
@Controller
public class RecordController {

    private final PersonalRecordService personalRecords;

    public RecordController(PersonalRecordService personalRecords) {
        this.personalRecords = personalRecords;
    }

    @GetMapping("/records")
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("records", personalRecords.list(user.getId(), null).stream().map(RecordFormatter::of).toList());
        return "records";
    }
}
