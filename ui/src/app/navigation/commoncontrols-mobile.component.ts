import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { AbstractSearchComponent } from './abstract-search.component';
import { SearchService } from '../search/search.service';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../auth/auth-service';

@Component({
  // tslint:disable-next-line:component-selector
  // eslint-disable-next-line @angular-eslint/component-selector -- legacy
  selector: '[app-commoncontrols-mobile]',
  templateUrl: './commoncontrols-mobile.component.html',
})
export class CommoncontrolsMobileComponent
  extends AbstractSearchComponent
  implements OnInit
{
  constructor(
    protected searchService: SearchService,
    protected route: ActivatedRoute,
    protected authService: AuthService,
    private location: Location
  ) {
    super(searchService, route, authService);
  }

  ngOnInit(): void {}

  isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  getCurrentUrl(): string {
    return this.location.path();
  }
}
