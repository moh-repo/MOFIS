import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { MenuChangeEvent } from './api/menuchangeevent';

@Injectable({
    providedIn: 'root'
})
export class MenuService {

    private menuSource = new Subject<MenuChangeEvent>();
    private resetSource = new Subject();
    private hasVisibleItemsSource = new BehaviorSubject<boolean>(true);

    menuSource$ = this.menuSource.asObservable();
    resetSource$ = this.resetSource.asObservable();
    hasVisibleItems$ = this.hasVisibleItemsSource.asObservable();

    onMenuStateChange(event: MenuChangeEvent) {
        this.menuSource.next(event);
    }

    reset() {
        this.resetSource.next(true);
    }

    setHasVisibleItems(hasItems: boolean) {
        this.hasVisibleItemsSource.next(hasItems);
    }
}
