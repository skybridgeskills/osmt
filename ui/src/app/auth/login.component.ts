import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { AppConfig } from '../app.config';

interface LoginQueryParams {
  return?: string;
}

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
})
export class LoginComponent implements OnInit {
  isSingleAuthMode = false;
  username = '';
  password = '';
  loginError = '';
  isLoading = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const loginUrl = AppConfig.settings.loginUrl;

    if (this.authService.isAuthenticated()) {
      this.router.navigate(['']);
      return;
    }

    // Check if single-auth mode based on authMode setting
    this.isSingleAuthMode = AppConfig.settings.authMode === 'single-auth';

    this.route.queryParams.subscribe(params => {
      const returnRoute = params.return;

      if (returnRoute) {
        this.authService.storeReturn(returnRoute);
      }

      // If not in single-auth mode, redirect to OAuth2
      if (!this.isSingleAuthMode) {
        // Normal OAuth2 flow
        window.location.href = loginUrl;
      }
      // If in single-auth mode, stay on page to show login form
    });
  }

  async onLogin(event?: Event): Promise<void> {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }

    if (!this.username || !this.password) {
      this.loginError = 'Please enter both username and password';
      return;
    }

    this.isLoading = true;
    this.loginError = '';

    try {
      await this.authService.login(this.username, this.password);
      const returnRoute = this.authService.popReturn() || '';
      this.router.navigate([returnRoute]);
    } catch (error: any) {
      this.loginError =
        error.error?.error || 'Login failed. Please check your credentials.';
    } finally {
      this.isLoading = false;
    }
  }
}
