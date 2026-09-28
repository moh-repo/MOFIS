import { Component, OnInit } from '@angular/core';
import { FaviconService } from './demo/service/favicon.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    standalone: false
})
export class AppComponent implements OnInit {
    // Ripple is configured once via providePrimeNG in AppModule; PrimeNGConfig was
    // removed in PrimeNG 18.

    constructor(private faviconService: FaviconService) { }

    ngOnInit(): void {
        // Sits at the root rather than in the layout: the tab icon should be the
        // system's on the login screen and the student portal too, not only once
        // someone is inside the admin shell.
        this.faviconService.sync();
    }
}
